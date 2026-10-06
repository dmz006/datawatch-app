#!/usr/bin/env python3
"""Minimal Ollama-compatible stub for the screenshot demo server.

Answers /api/generate, /api/chat and /api/tags with a canned Automaton plan so
`POST /api/autonomous/prds/{id}/decompose` succeeds without a real model.
Listens on 127.0.0.1:11434 (CI runner only).
"""
import json
import os
from http.server import BaseHTTPRequestHandler, HTTPServer

PLAN = {
    "title": "Weather API: response caching",
    "stories": [
        {
            "title": "Cache forecast lookups",
            "description": "Add an in-memory TTL cache in front of the forecast provider so repeated city lookups are served locally.",
            "tasks": [
                {"title": "Add TTLCache helper", "spec": "Create weather_api/cache.py with a small TTL cache (get/set/expire) and unit tests."},
                {"title": "Wire cache into get_forecast", "spec": "Use the cache in weather_api/forecast.py; cache key is the normalized city name."},
            ],
        },
        {
            "title": "Expose cache metrics",
            "description": "Report hit/miss counters on the /health endpoint.",
            "tasks": [
                {"title": "Count hits and misses", "spec": "Track hit/miss counters in TTLCache and expose them via stats()."},
                {"title": "Extend /health payload", "spec": "Include cache stats in the /health JSON response and update tests."},
            ],
        },
    ],
}
PLAN_TEXT = json.dumps(PLAN)

# Second Automaton (spec mentions the 7-day endpoint).
WEEK_PLAN_TEXT = json.dumps({
    "title": "Weather API: 7-day forecast",
    "stories": [
        {
            "title": "Add /forecast/week endpoint",
            "description": "Return a 7-day forecast for a city with input validation.",
            "tasks": [
                {"title": "Implement get_week_forecast", "spec": "Add get_week_forecast(city) to weather_api/forecast.py returning 7 daily entries."},
                {"title": "Validate city input", "spec": "Reject empty or overlong city names with a 400 response."},
                {"title": "Tests for the week endpoint", "spec": "Cover happy path, unknown city and validation errors."},
            ],
        },
    ],
})


class Handler(BaseHTTPRequestHandler):
    def _send(self, obj):
        body = json.dumps(obj).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path.startswith("/api/tags"):
            self._send({"models": [{"name": "demo-planner", "model": "demo-planner", "size": 0}]})
        elif self.path.startswith("/api/version"):
            self._send({"version": "0.0.0-demo"})
        else:
            self._send({})

    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length) if length else b""
        text = WEEK_PLAN_TEXT if b"/forecast/week" in body else PLAN_TEXT
        if self.path.startswith("/api/chat"):
            self._send({"model": "demo-planner", "message": {"role": "assistant", "content": text}, "done": True})
        else:
            self._send({"model": "demo-planner", "response": text, "done": True})

    def log_message(self, fmt, *args):
        print("ollama-stub:", fmt % args, flush=True)


if __name__ == "__main__":
    HTTPServer(("127.0.0.1", int(os.environ.get("OLLAMA_STUB_PORT", "11434"))), Handler).serve_forever()
