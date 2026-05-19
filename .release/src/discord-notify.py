#!/usr/bin/env python3
"""Discord notification for plugin releases. Reads config from env vars and YAML."""
import os, sys, json
from datetime import datetime

try:
    import yaml
except ImportError:
    print("PyYAML not installed, trying pip...")
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "pyyaml"])
    import yaml

def main():
    webhook_url = os.environ.get('DISCORD_WEBHOOK_URL', '')
    if not webhook_url:
        print("No DISCORD_WEBHOOK_URL set, skipping")
        return

    version = os.environ['VERSION']
    repo_name = os.environ.get('REPO_NAME', 'unknown')
    repo_owner = os.environ.get('REPO_OWNER', 'castledking')

    payload = {}

    config_path = os.environ.get('DISCORD_CONFIG_PATH', '.release/discord-updater-webhook.yml')
    config = {}
    if os.path.exists(config_path):
        with open(config_path) as f:
            config = yaml.safe_load(f) or {}

    if 'message' in config:
        msg = config['message']
        if msg.get('username'): payload['username'] = msg['username']
        if msg.get('avatar_url'): payload['avatar_url'] = msg['avatar_url']
        if msg.get('content'): payload['content'] = msg['content']

    embed = {}
    notes_path = os.environ.get('RELEASE_NOTES_PATH', f".release/v{version}.md")
    if os.path.exists(notes_path):
        with open(notes_path) as f:
            lines = f.readlines()
        embed['title'] = lines[0].strip() if lines else f"{repo_name} v{version}"
        body = ''.join(lines[1:]).strip()
        if len(body) > 4000: body = body[:4000] + "..."
        embed['description'] = body if body else "No release notes available."
    else:
        embed['title'] = f"{repo_name} v{version}"
        embed['description'] = "No release notes available."

    embed['url'] = f"https://github.com/{repo_owner}/{repo_name}/releases/tag/{version}"

    if 'embed' in config:
        ec = config['embed']
        if ec.get('color'): embed['color'] = ec['color']
        if ec.get('author'):
            embed['author'] = {
                'name': ec['author'].get('name', ''),
                'url': ec['author'].get('url', ''),
                'icon_url': ec['author'].get('icon_url', '')
            }
        if ec.get('thumbnail', {}).get('url'):
            embed['thumbnail'] = {'url': ec['thumbnail']['url']}
        if ec.get('image', {}).get('url'):
            embed['image'] = {'url': ec['image']['url']}
        if ec.get('footer'):
            embed['footer'] = {
                'text': ec['footer'].get('text', ''),
                'icon_url': ec['footer'].get('icon_url', '')
            }
        if ec.get('timestamp', True):
            embed['timestamp'] = datetime.utcnow().isoformat() + 'Z'
        if 'fields' in ec:
            embed['fields'] = []
            for field in ec['fields']:
                embed['fields'].append({
                    'name': field.get('name', ''),
                    'value': field.get('value', ''),
                    'inline': field.get('inline', False)
                })

    payload['embeds'] = [embed]
    payload_json = json.dumps(payload)

    import urllib.request
    req = urllib.request.Request(
        webhook_url, data=payload_json.encode('utf-8'),
        headers={'Content-Type': 'application/json'},
        method='POST')

    try:
        resp = urllib.request.urlopen(req)
        print(f"Discord notification sent (HTTP {resp.status})")
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        print(f"Discord webhook error: {e.code} {body}")
        sys.exit(1)

    # Edit mode
    if 'edit' in config and config['edit'].get('message_id'):
        msg_id = config['edit']['message_id']
        if msg_id:
            edit_req = urllib.request.Request(
                f"{webhook_url}/messages/{msg_id}",
                data=payload_json.encode('utf-8'),
                headers={'Content-Type': 'application/json'},
                method='PATCH')
            try:
                resp = urllib.request.urlopen(edit_req)
                print(f"Discord message {msg_id} updated (HTTP {resp.status})")
            except urllib.error.HTTPError as e:
                body = e.read().decode()
                print(f"Discord edit error: {e.code} {body}")

if __name__ == '__main__':
    main()
