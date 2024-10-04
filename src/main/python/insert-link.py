import os
import re

# Directory containing the .txt files
directory = 'src/main/resources/'

# Patterns to search for
patterns = [
  r"""(['"`])(.+?)\1""",    # Matches 'function', "function", or `function`
  r"""\*\*(.+?)\*\*""",     # Matches **function**
]

# Iterate over each .txt file in the directory
for filename in os.listdir(directory):
  if filename.endswith('.txt'):
    filepath = os.path.join(directory, filename)
    # Get NAME by stripping .txt from filename
    NAME = os.path.splitext(filename)[0]

    # Read the content of the file
    with open(filepath, 'r', encoding='utf-8') as file:
      content = file.read()

    function = None
    all_matches = []

    # Collect all matches from all patterns
    for pattern in patterns:
      for match in re.finditer(pattern, content):
        start = match.start()
        # Extract the function name based on the pattern
        if pattern == patterns[0]:
          # For pattern r"""(['"`])(.+?)\1"""
          function_name = match.group(2)
        elif pattern == patterns[1]:
          # For pattern r"""\*\*(.+?)\*\*"""
          function_name = match.group(1)
        else:
          function_name = None

        if function_name:
          # Append the match with its start position
          all_matches.append((start, function_name))

    if not all_matches:
      print(f"No function name found in {filename}. Skipping.")
      continue

    # Sort matches by their position in the content
    all_matches.sort(key=lambda x: x[0])

    # Select the function name from the earliest match
    function = all_matches[0][1]

    # Sentence to append
    sentence = f"\nMore information in the documentation [{function}](https://warpfleet.senx.io/browse/io.warp10/warp10-ext-forecasting/2.0.0/io.warp10/warp10-ext-forecasting/{NAME}.mc2)\n"

    # Append the sentence to the file
    with open(filepath, 'a', encoding='utf-8') as file:
      file.write(sentence)

    print(f"Appended documentation link to {filename}.")
