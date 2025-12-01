#! /usr/bin/env python3
# -*- coding: utf-8 -*-

# Original script from https://github.com/compiler-explorer/compiler-explorer
#
# Licensed under BSD 2-Cause License
# BSD 2-Clause License
#
# Copyright (c) 2012-2022, Compiler Explorer Authors
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this
#    list of conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright notice,
#    this list of conditions and the following disclaimer in the documentation
#    and/or other materials provided with the distribution.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
# OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

import argparse
import json
import os
import re
import sys
import tarfile
import urllib
from urllib import request, parse

try:
    from bs4 import BeautifulSoup
except ImportError:
    raise ImportError("Please install BeautifulSoup (apt-get install python3-bs4 or pip install beautifulsoup4 should do it)")
try:
    import html2text
except ImportError:
    raise ImportError("Please install html2text (pip install html2text should do it)")

parser = argparse.ArgumentParser(description='Docenizes XML version of the official ARM documents')
parser.add_argument('-i', '--inputfolder', type=str,
                    help='Folder where the input files reside as .xml. Default is ./aarch64-docs/',
                    default='aarch64-docs')
parser.add_argument('-o', '--outputpath', type=str, help='Final path of the .kt file. Default is ./Aarch64Docs.kt',
                    default='./Aarch64Docs.kt')
parser.add_argument('-d', '--downloadfolder', type=str,
                    help='Folder where the archive will be downloaded and extracted', default='aarch64-docs')
parser.add_argument('-c', '--configfile', type=str, help='Json configuration file with contants', default='aarch64.json', required=True)

# The maximum number of paragraphs from the description to copy.
MAX_DESC_PARAS = 5
STRIP_SUFFIX = re.compile(r'\s*(\(.*\))?\s*(--.*)?')

#arm32
FLDMX_RE = re.compile(r'^(FLDM)(\*)(X)')
FLDMX_SET = set(['DB', 'IA'])

#aarch64
CONDITION_RE = re.compile(r'^([A-Z][A-Z0-9]*\.?)(cond|<cc>)()')
CONDITION_SET = set(['EQ', 'NE', 'CS', 'CC', 'MI', 'PL', 'VS', 'VC', 'HI', 'LS', 'GE', 'LT', 'GT', 'LE', 'AL'])
FRINT_RE = re.compile(r'^(FRINT)(<r>)()')
FRINT_SET = set(['N', 'A', 'M', 'P', 'A', 'I', 'X'])

EXPAND_RE = [(FLDMX_RE, FLDMX_SET), (CONDITION_RE, CONDITION_SET), (FRINT_RE, FRINT_SET)]

# Some instructions are so broken we just take their names from the filename
UNPARSEABLE_INSTR_NAMES = []
# Some files contain instructions which cannot be parsed and which compilers are unlikely to emit
IGNORED_FILE_NAMES = [ ]
# Some instructions are defined in multiple files. We ignore a specific set of the
# duplicates here.
IGNORED_DUPLICATES = []


class Config:
    class Archive:
        url : str
        name : str
        subdir : str
        def __init__(self, *, url, name, subdir):
            self.url = str(url)
            self.name = str(name)
            self.subdir = str(subdir)

    archive : Archive
    documentation : str
    isa: str
    def __init__(self, *, archive, documentation, isa):
        self.archive = Config.Archive(**archive)
        self.documentation = str(documentation)
        self.isa = str(isa)


class Instruction(object):
    def __init__(self, name, heading, names, brief, tooltip, body):
        self.name = name
        self.heading = heading
        self.names = names
        self.brief = brief
        self.tooltip = tooltip.rstrip(': ,')
        self.body = body

    def __str__(self):
        return "{} = {}\n{}".format(self.names, self.tooltip, self.body)


def get_url_for_instruction(instr):
    return "{}{}--{}-".format(
        config.documentation,
        re.sub("[^a-zA-Z]", "-", instr.heading),
        re.sub("[^a-zA-Z]", "-", instr.brief).capitalize()
    )


def download_asm_doc_archive(downloadfolder):
    if not os.path.exists(downloadfolder):
        print("Creating {} as download folder".format(downloadfolder))
        os.makedirs(downloadfolder)
    elif not os.path.isdir(downloadfolder):
        print("Error: download folder {} is not a directory".format(downloadfolder))
        sys.exit(1)
    archive_name = os.path.join(downloadfolder, config.archive.name)
    print("Downloading archive...")
    urllib.request.urlretrieve(config.archive.url, archive_name)


def extract_asm_doc_archive(downloadfolder, inputfolder):
    print("Extracting file...")
    if os.path.isdir(os.path.join(inputfolder, config.archive.subdir)):
        for root, dirs, files in os.walk(os.path.join(inputfolder, config.archive.subdir)):
            for file in files:
                if os.path.splitext(file)[1] == ".xml":
                    os.remove(os.path.join(root, file))
    tar = tarfile.open(os.path.join(downloadfolder, config.archive.name))
    tar.extractall(path=inputfolder)

def convert_code(element):
    for child in element:
        name = child.name
        if name == "value" or name == "instruction":
            child.name = "code"

def get_description_paragraphs(document_soup, part):
    if part is None:
        return None
    for image in part.find_all('image'):
        image.decompose()

    description_paragraphs = []
    for element in part:
        name = element.name
        if name == "para":
            paragraph = element
            paragraph = paragraph.wrap(document_soup.new_tag('p'))
            paragraph.para.unwrap()
            convert_code(paragraph)
            description_paragraphs.append(paragraph)
        elif name == "list":
            element.name = "ul"
            element.attrs = None
            for item in element.find_all('listitem'):
                item.content.unwrap()
                item.name = "li"
                convert_code(item)
            description_paragraphs.append(element)

    return description_paragraphs

def to_markdown(body):
    html = ''.join(map(lambda x: str(x), body)).strip()
    h = html2text.HTML2Text()
    return h.handle(html)

instrclasses = set()

def parse(filename, f):
    doc = BeautifulSoup(f, 'html.parser')
    if doc.instructionsection is None:
        print(filename + ": Failed to find instructionsection")
        return None
    instructionsection = doc.instructionsection
    names = set()

    heading = doc.heading
    if heading is None:
        heading = filename
    else:
        heading = heading.text.strip()

    for name in STRIP_SUFFIX.sub('',instructionsection['title']).split(','):
        name = name.strip()
        names.add(name)
        for RE, SET in EXPAND_RE:
            match = RE.match(name)
            if match:
                for elt in SET:
                    names.add(match.group(1) + elt + match.group(3))

    brief = doc.instructionsection.desc.brief
    if brief.para is None:
        brief = ""
    else:
        brief.para.unwrap()
        brief = brief.text.strip()

    body = get_description_paragraphs(doc, instructionsection.desc.authored)
    if body is None:
        body = get_description_paragraphs(doc, instructionsection.desc.description)
    if body is None:
        return None

    return Instruction(
        filename,
        heading,
        names,
        brief,
        body[0].text.strip(),
        to_markdown(body))

def parse_xml(directory):
    print("Parsing instructions...")
    instructions = []
    for root, dirs, files in os.walk(directory):
        for file in files:
            if file.endswith(".xml") and file != "onebigfile.xml":
                with open(os.path.join(root, file), encoding='utf-8') as f2:
                    name = os.path.splitext(file)[0]
                    if name in IGNORED_DUPLICATES or name in IGNORED_FILE_NAMES:
                        continue
                    instruction = parse(name, f2)
                    if not instruction:
                        continue
                    instructions.append(instruction)
    return instructions


def self_test(instructions, directory):
    # For each generated instruction, check that there is a path to a file in
    # the documentation.
    directory = os.path.join(directory, config.archive.subdir)
    ok = True
    for inst in instructions:
        if not os.path.isfile(os.path.join(directory, inst.name + ".xml")):
            print("Warning: {} has not file associated".format(inst.name))
            ok = False
    return ok


def docenizer():
    global config
    args = parser.parse_args()
    print("Called with: {}".format(args))

    with open(args.configfile) as f:
        config = Config(**json.load(f))
    print("Use configs: {}".format(json.dumps(config, default=lambda o: o.__dict__)))
    # If we don't have the html folder already...
    if not os.path.isdir(os.path.join(args.inputfolder, config.archive.subdir)):
        # We don't, try with the compressed file
        if not os.path.isfile(os.path.join(args.downloadfolder, config.archive.name)):
            # We can't find that either. Download it
            try:
                download_asm_doc_archive(args.downloadfolder)
                extract_asm_doc_archive(args.downloadfolder, args.inputfolder)
            except IOError as e:
                print("Error when downloading archive:")
                print(e)
                sys.exit(1)
        else:
            # We have a file already downloaded
            extract_asm_doc_archive(args.downloadfolder, args.inputfolder)
    instructions = parse_xml(os.path.join(args.inputfolder, config.archive.subdir))
    print(instrclasses)
    instructions.sort(key=lambda b: b.name)
    self_test(instructions, args.inputfolder)
    all_inst = set()
    for inst in instructions:
        if not all_inst.isdisjoint(inst.names):
            print("Overlap in instruction names: {} for {}".format(
                inst.names.intersection(all_inst), inst.name))
        all_inst = all_inst.union(inst.names)
    if not self_test(instructions, args.inputfolder):
        print("Tests do not pass. Not writing output file. Aborting.")
        sys.exit(3)
    print("Writing {} instructions".format(len(instructions)))
    with open(args.outputpath, 'w') as f:
        f.write("""
// NOTE: AUTO-GENERATED FILE, DO NOT MODIFY!

package dev.romainguy.kotlin.explorer.code

import androidx.collection.MutableScatterMap

""".lstrip())
        f.write("val {}Docs = MutableScatterMap<String, OpCodeDoc>({}).apply {{\n".format(config.isa.capitalize(), len(instructions)))
        seen = {}
        for inst in instructions:
            for name in sorted(inst.names):
                if (name in seen) and (inst.heading.find("(immediate)") == -1) and (inst.heading.find("(register)") == -1): break
                seen[name] = True
                f.write("    this[\"{}\"] = OpCodeDoc(\n".format(name.lower()))
                f.write("        \"{}\",\n".format(name))
                f.write("        \"\"\"{}\"\"\",\n".format(inst.body))
                f.write("        \"{}\"\n".format(get_url_for_instruction(inst)))
                f.write("    )\n")
        f.write("} as ScatterMap<String, OpCodeDoc>\n")
if __name__ == '__main__':
    docenizer()
