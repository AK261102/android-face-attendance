#!/usr/bin/env python3
"""
Exports the Claude Code session transcript for this project to a single readable JSON file,
which is one of the assignment's submission items.

The raw transcript is JSONL and carries harness bookkeeping plus base64 image payloads (the
assignment PDF, and the face photos read while validating the model). Those are stripped:
images become a short placeholder and only conversational records are kept.

Usage:
    python tools/export_conversation.py <transcript.jsonl> docs/conversation.json

The transcript lives in:
    ~/.claude/projects/<slugified-project-path>/<session-id>.jsonl
"""
import json
import os
import sys

# Harness bookkeeping records that carry no conversation.
SKIP_TYPES = {
    "mode",
    "permission-mode",
    "atis-latch",
    "last-prompt",
    "ai-title",
    "queue-operation",
    "file-history-snapshot",
}

MAX_TEXT = 20000  # truncate very long tool outputs


def _truncate_json(value):
    """Serialise a tool input, truncating if it is unreasonably large."""
    dumped = json.dumps(value, default=str)
    if len(dumped) <= MAX_TEXT:
        return value
    return {"_truncated": dumped[:MAX_TEXT]}


def clean(block):
    """Normalise one content block, dropping binary payloads."""
    if not isinstance(block, dict):
        return {"type": "text", "text": str(block)[:MAX_TEXT]}

    kind = block.get("type")

    if kind == "image":
        media_type = block.get("source", {}).get("media_type", "unknown")
        return {"type": "image", "note": f"<image omitted: {media_type}>"}

    if kind == "text":
        return {"type": "text", "text": (block.get("text") or "")[:MAX_TEXT]}

    if kind == "thinking":
        return {"type": "thinking", "text": (block.get("thinking") or "")[:MAX_TEXT]}

    if kind == "tool_use":
        return {
            "type": "tool_use",
            "name": block.get("name"),
            "input": _truncate_json(block.get("input", {})),
        }

    if kind == "tool_result":
        content = block.get("content")
        if isinstance(content, list):
            content = [clean(c) for c in content]
        elif isinstance(content, str):
            content = content[:MAX_TEXT]
        return {
            "type": "tool_result",
            "is_error": block.get("is_error", False),
            "content": content,
        }

    return {"type": kind or "unknown"}


def export(src, dst):
    messages = []
    with open(src) as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                record = json.loads(line)
            except json.JSONDecodeError:
                continue
            if record.get("type") in SKIP_TYPES:
                continue

            message = record.get("message")
            if not isinstance(message, dict):
                continue

            content = message.get("content")
            if isinstance(content, str):
                content = [{"type": "text", "text": content[:MAX_TEXT]}]
            elif isinstance(content, list):
                content = [clean(block) for block in content]
            else:
                continue

            messages.append(
                {
                    "role": message.get("role", record.get("type")),
                    "timestamp": record.get("timestamp"),
                    "content": content,
                }
            )

    payload = {
        "project": "Android Attendance App - hiring assignment",
        "tool": "Claude Code",
        "session_id": os.path.basename(src).replace(".jsonl", ""),
        "message_count": len(messages),
        "note": (
            "Image payloads (the assignment PDF and the face photos used for model "
            "validation) are omitted; very long tool outputs are truncated."
        ),
        "messages": messages,
    }

    os.makedirs(os.path.dirname(dst) or ".", exist_ok=True)
    with open(dst, "w") as handle:
        json.dump(payload, handle, indent=2, ensure_ascii=False)

    size_mb = os.path.getsize(dst) / 1e6
    print(f"{len(messages)} messages -> {dst} ({size_mb:.1f} MB)")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    export(sys.argv[1], sys.argv[2])
