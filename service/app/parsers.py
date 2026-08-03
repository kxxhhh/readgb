"""Structured parsers for explicitly fetched public HTML pages.

The parsers only transform HTML supplied by the caller. They do not perform
network requests, execute scripts, or attempt to access authenticated pages.
"""

from dataclasses import dataclass
import hashlib
import re
from urllib.parse import urldefrag, urljoin

from bs4 import BeautifulSoup, Tag

from .models import Item, KnowledgeEntry


@dataclass(frozen=True)
class CatalogNode:
    id: str
    title: str
    level: str
    source_url: str
    metadata: str = ""


def _clean_text(value: str | None) -> str:
    return re.sub(r"\s+", " ", value or "").strip()


def _text(node: Tag | None, selectors: tuple[str, ...]) -> str:
    if node is None:
        return ""
    for selector in selectors:
        match = node.select_one(selector)
        value = _clean_text(match.get_text(" ", strip=True) if match else "")
        if value:
            return value
    return ""


def _attribute(node: Tag | None, *names: str) -> str:
    if node is None:
        return ""
    for name in names:
        value = node.get(name)
        if value is not None:
            value = _clean_text(str(value))
            if value:
                return value
    return ""


def _absolute_url(base_url: str, href: str) -> str:
    return urldefrag(urljoin(f"{base_url.rstrip('/')}/", href))[0]


def _stable_id(prefix: str, title: str, source_url: str) -> str:
    digest = hashlib.sha1(f"{title}|{source_url}".encode("utf-8")).hexdigest()[:16]
    return f"{prefix}-{digest}"


def _catalog_level(anchor: Tag) -> str:
    level = _attribute(anchor, "data-level", "data-type")
    if level in {"section", "volume", "year"}:
        return level
    context = " ".join(
        [
            _attribute(anchor, "href"),
            _attribute(anchor, "class"),
            _clean_text(anchor.get_text(" ", strip=True)),
        ]
    ).lower()
    if any(token in context for token in ("year", "nian", "年")):
        return "year"
    if any(token in context for token in ("volume", "juan", "卷")):
        return "volume"
    return "section"


def parse_main_catalog(html: str, base_url: str) -> list[CatalogNode]:
    """Parse catalog links marked by semantic attributes or catalog classes."""

    soup = BeautifulSoup(html, "html.parser")
    root = soup.select_one("[data-catalog], .catalog, main") or soup
    nodes: list[CatalogNode] = []
    seen_urls: set[str] = set()
    for anchor in root.select("a[href]"):
        href = _attribute(anchor, "href")
        title = _clean_text(anchor.get_text(" ", strip=True))
        if not href or href.startswith(("#", "javascript:", "mailto:")) or len(title) < 2:
            continue
        source_url = _absolute_url(base_url, href)
        if source_url in seen_urls:
            continue
        seen_urls.add(source_url)
        nodes.append(
            CatalogNode(
                id=_attribute(anchor, "data-id") or _stable_id("catalog", title, source_url),
                title=title,
                level=_catalog_level(anchor),
                source_url=source_url,
                metadata=_attribute(anchor, "data-era", "data-dynasty"),
            )
        )
    return nodes


def _entry_nodes(soup: BeautifulSoup, selector: str) -> list[Tag]:
    nodes = soup.select(selector)
    if nodes:
        return nodes
    return [soup]


def _tags(node: Tag) -> tuple[str, ...]:
    values: list[str] = []
    for tag in node.select("[data-tag], .tag, .tags a"):
        value = _clean_text(tag.get_text(" ", strip=True))
        if value and value not in values:
            values.append(value)
    return tuple(values)


def parse_reading_entries(
    html: str,
    base_url: str,
    *,
    section: str = "资治通鉴",
    volume_id: str | None = None,
    year_id: str | None = None,
) -> list[Item]:
    """Parse public reading cards or article blocks into domain items."""

    soup = BeautifulSoup(html, "html.parser")
    entries = _entry_nodes(soup, "[data-entry], article, .entry, .reading-entry, .article-entry")
    items: list[Item] = []
    seen_ids: set[str] = set()
    for sort_order, entry in enumerate(entries, start=1):
        title = _attribute(entry, "data-title") or _text(entry, ("h1", "h2", "h3", ".entry-title", ".article-title"))
        if not title:
            continue
        link = entry.select_one("a[href]")
        source_url = _absolute_url(base_url, _attribute(entry, "data-url", "data-source-url") or _attribute(link, "href") or base_url)
        item_id = _attribute(entry, "data-id") or _stable_id("reading", title, source_url)
        if item_id in seen_ids:
            continue
        seen_ids.add(item_id)
        original = _text(entry, ("[data-original]", ".original", ".text-original"))
        translation = _text(entry, ("[data-translation]", ".translation", ".vernacular", ".text-translation"))
        notes = _text(entry, ("[data-notes]", ".notes", ".commentary", ".annotations"))
        content = _text(entry, ("[data-content]", ".content", ".article-content", ".entry-content"))
        if not content:
            content = translation or original or _clean_text(entry.get_text(" ", strip=True))
        summary = _text(entry, ("[data-summary]", ".summary", ".excerpt")) or content[:160]
        dynasty = _attribute(entry, "data-dynasty") or _text(entry, (".dynasty", "[data-dynasty]"))
        category = _attribute(entry, "data-category") or section
        items.append(
            Item(
                id=item_id,
                title=title,
                category=category,
                dynasty=dynasty,
                summary=summary,
                content=content,
                source_url=source_url,
                updated_at=_attribute(entry, "data-updated-at", "data-updated") or "",
                section=section,
                volume_id=volume_id,
                year_id=year_id,
                original=original,
                translation=translation,
                notes=notes,
                tags=_tags(entry),
                sort_order=sort_order,
            )
        )
    return items


def parse_knowledge_index(html: str, base_url: str) -> list[KnowledgeEntry]:
    """Parse public knowledge cards into knowledge entries."""

    soup = BeautifulSoup(html, "html.parser")
    entries = _entry_nodes(soup, "[data-knowledge-entry], article, .knowledge-entry, .knowledge-card")
    result: list[KnowledgeEntry] = []
    seen_ids: set[str] = set()
    for entry in entries:
        title = _attribute(entry, "data-title") or _text(entry, ("h1", "h2", "h3", ".title", ".entry-title"))
        if not title:
            continue
        link = entry.select_one("a[href]")
        source_url = _absolute_url(base_url, _attribute(entry, "data-url", "data-source-url") or _attribute(link, "href") or base_url)
        entry_id = _attribute(entry, "data-id") or _stable_id("knowledge", title, source_url)
        if entry_id in seen_ids:
            continue
        seen_ids.add(entry_id)
        summary = _text(entry, ("[data-summary]", ".summary", ".excerpt"))
        content = _text(entry, ("[data-content]", ".content", ".entry-content")) or summary
        result.append(
            KnowledgeEntry(
                id=entry_id,
                title=title,
                category=_attribute(entry, "data-category") or _text(entry, (".category", "[data-category]")),
                summary=summary or content[:160],
                content=content,
                source_url=source_url,
                updated_at=_attribute(entry, "data-updated-at", "data-updated") or "",
            )
        )
    return result
