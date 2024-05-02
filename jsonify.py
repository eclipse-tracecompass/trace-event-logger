#!/usr/bin/env python3

# Install dependencies:
# pip install progressbar json_repair

__author__ = "Matthew Khouzam"
__copyright__ = "Copyright 2024, Ericsson"
__credits__ = ["Matthew Khouzam"]
__license__ = "MIT"

import re
import argparse
import progressbar
from json_repair import repair_json

widgets = [' [',
         progressbar.Timer(format= 'elapsed time: %s'),
         '] ',
           progressbar.Bar('#'),' (',
           progressbar.ETA(), ') ',
          ]

ticks = 360
  
event_start = re.compile(r'\{\"ts\":')
parser = argparse.ArgumentParser()
parser.add_argument("input", help="input file", type=str)
parser.add_argument("output", help="output file", type=str)
args = parser.parse_args()
with open(args.input, 'r') as input:
    with open(args.output, 'w') as output:
        events = []
        output.write('[')
        pre_lines = input.readlines()
        lines = []
        for line in pre_lines:
            lines += line.split('""')
        tick_line = len(lines)
        pre_lines = None
        bar = progressbar.ProgressBar(maxval=tick_line, widgets=widgets).start()
        for pos, line in enumerate(lines):
            bar.update(pos)
            length = len(line)
            event = re.search(event_start, line) 
            leftover = ticks
            while event is not None:
                bracket_count = 1
                if event is not None:
                    index_start = event.start()
                    index = event.end()
                    for char in line[index:]:
                        index += 1
                        if char == '{':
                            bracket_count +=1
                        elif char == '}':
                            bracket_count -=1
                        if bracket_count == 0:
                            # repair_json() is useful to clean-up slightly illegal
                            # JSON.  e.g. unescaped chars in JSON string
                            event_line = repair_json(line[index_start:index].replace('‥', ':'))
                            events.append(event_line)
                            break
                    line = line[index:]
                    event = re.search(event_start, line)        
        output.write(',\n'.join(events))
        output.write(']\n')
print( f'\nWrote to {args.output}')
