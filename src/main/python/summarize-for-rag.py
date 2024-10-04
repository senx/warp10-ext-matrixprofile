import os
import requests
import json

# env

debug = False
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
if not OPENAI_API_KEY:
  raise ValueError("OPENAI_API_KEY not set")

#
# Model definition and API endpoint
#

api_url = 'https://api.openai.com/v1/chat/completions'
model = 'gpt-4o-mini'
instructions = "Extract and revise the information from the provided document into a single paragraph that includes the function name, a description of its purpose, each parameters from params with their descriptions, an exhaustive listing of the related functions, and the associated tags and categories. GTS stands for Geo Time Series. You can extrapolate on categories. Do not extract 'since' and 'deprecated'."
headers = {
  'Content-Type': 'application/json',
  'Authorization': f'Bearer {OPENAI_API_KEY}',
}

#
# Loop through files and send them to LLM to summarize them
#

c = 0
max_files = 99999 # to test
token_count = 0 # to monitor cost
directory = 'src/main/warpscript/io.warp10/warp10-ext-matrixprofile/'
out_directory = 'src/main/resources/'
for filename in os.listdir(directory):
  if filename.endswith('.mc2'):
    filepath = os.path.join(directory, filename)
    summary_filename = f"{os.path.splitext(filename)[0]}.txt"
    summary_filepath = os.path.join(out_directory, summary_filename)
    
    # Check if the summary file already exists
    if os.path.exists(summary_filepath):
      #print(f"Skipping {filename} as summary already exists.")
      continue

    # Process the file
    with open(filepath, 'r', encoding='utf-8') as file:
      content = file.read()

    # Call API
    data = {
      "model": model,
      "messages": [
        {"role": "user", "content": f"{instructions}\n\nDocument:\n{content}"}
      ],
    }
    response = requests.post(api_url, headers=headers, data=json.dumps(data))

    # retry if error, likely max content length was attained
    if response.status_code != 400:

      # truncate after end of macro
      marker = "'macro' STORE"
      if marker in content:
        index = content.find(marker)
        content = content[:index]

      # retry
      data = {
        "model": model,
        "messages": [
          {"role": "user", "content": f"{instructions}\n\nDocument:\n{content}"}
        ],
      }
      response = requests.post(api_url, headers=headers, data=json.dumps(data))

    # Check for successful response
    if response.status_code == 200:
      result = response.json()
      summary = result['choices'][0]['message']['content'].strip()
      
      if debug:
        print(result)

      # Save the summary to a new file
      with open(summary_filepath, 'w', encoding='utf-8') as summary_file:
        summary_file.write(summary)
      
      # Update total tokens used
      usage = result.get('usage', {})
      token_count += usage.get('total_tokens', 0)

    else:
      # Handle errors
      error_message = response.json().get('error', {}).get('message', 'An error occurred.')
      print(f"Failed to summarize {filename}. Error: {response.status_code} - {error_message}")

    # count files
    c += 1
    if c >= max_files:
      break

    # After each 10 files, print the total tokens and number of files processed
    if c % 10 == 0:
      print(f"Processed {c} files.")
      print(f"Total tokens used so far: {token_count}\n")

print(f"Processed {c} files.")
print(f"Total tokens used so far: {token_count}\n")

