"""Normalize public API records before they are shown in the reader."""

import json
import re
from dataclasses import replace
from typing import Any

from .models import Item


_PARAGRAPH_NUMBER = re.compile(r"^\s*\d+\s*")


def paragraph_prefix_length(value: str) -> int:
    match = _PARAGRAPH_NUMBER.match(value or "")
    return match.end() if match else 0


def strip_paragraph_number(value: str) -> str:
    value = value or ""
    match = _PARAGRAPH_NUMBER.match(value)
    normalized = value[match.end():].strip() if match else value.strip()
    return normalized or value.strip()


def _shift_start_indices(value: Any, offset: int) -> Any:
    if isinstance(value, dict):
        return {
            key: max(0, child - offset)
            if key == "start_index" and isinstance(child, int) and not isinstance(child, bool)
            else _shift_start_indices(child, offset)
            for key, child in value.items()
        }
    if isinstance(value, list):
        return [_shift_start_indices(child, offset) for child in value]
    return value


def _normalize_notes(notes: str, offset: int) -> str:
    if not notes.strip() or offset <= 0:
        return notes
    try:
        payload = json.loads(notes)
    except (TypeError, json.JSONDecodeError):
        return notes
    return json.dumps(_shift_start_indices(payload, offset), ensure_ascii=False, separators=(",", ":"))


def normalize_tongjian_item(item: Item) -> Item:
    """Remove API paragraph numbers and align stored annotation offsets."""
    if item.section not in {"资治通鉴", "纪事本末", "读通鉴论"} or not item.id.startswith(("zztj-", "jishi-item-", "lun-item-", "tongjian-jishi-", "dutongjian-lun-")):
        return item
    raw_text = item.original or item.content
    offset = paragraph_prefix_length(raw_text)
    return replace(
        item,
        content=strip_paragraph_number(item.content),
        original=strip_paragraph_number(item.original),
        notes=_normalize_notes(item.notes, offset),
    )
