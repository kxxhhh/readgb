from email.message import Message
from urllib.error import HTTPError, URLError

from app.crawler import RobotsAwareFetcher


def test_fetcher_refuses_disallowed_url_before_opening():
    opened = False

    def opener(_request, timeout):
        nonlocal opened
        opened = True
        raise AssertionError("disallowed URL was opened")

    fetcher = RobotsAwareFetcher(
        "https://example.com",
        opener=opener,
        robots_checker=lambda _url: False,
    )

    assert fetcher.fetch("/private") is None
    assert opened is False


def test_fetcher_retries_with_backoff_and_caches_success():
    calls = 0

    def opener(_request, timeout):
        nonlocal calls
        calls += 1
        if calls == 1:
            raise URLError("temporary failure")

        class Response:
            def read(self):
                return b"<html>ok</html>"

            def __enter__(self):
                return self

            def __exit__(self, *_args):
                return False

        return Response()

    fetcher = RobotsAwareFetcher(
        "https://example.com",
        opener=opener,
        robots_checker=lambda _url: True,
        sleep=lambda _seconds: None,
        retries=2,
    )

    assert fetcher.fetch("/article") == "<html>ok</html>"
    assert fetcher.fetch("/article") == "<html>ok</html>"
    assert calls == 2


def test_fetcher_honors_rate_limit_backoff():
    calls = 0
    delays = []
    headers = Message()
    headers["Retry-After"] = "17"

    def opener(_request, timeout):
        nonlocal calls
        calls += 1
        if calls == 1:
            raise HTTPError("https://example.com/article", 429, "rate limited", headers, None)

        class Response:
            def read(self):
                return b"<html>ok</html>"

            def __enter__(self):
                return self

            def __exit__(self, *_args):
                return False

        return Response()

    fetcher = RobotsAwareFetcher(
        "https://example.com",
        opener=opener,
        robots_checker=lambda _url: True,
        sleep=delays.append,
        retries=2,
    )

    assert fetcher.fetch("/article") == "<html>ok</html>"
    assert calls == 2
    assert delays == [17.0]
