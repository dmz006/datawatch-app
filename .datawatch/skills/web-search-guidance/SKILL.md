---
name: web-search-guidance
description: Guidance for using the web_search MCP tool effectively in this session.
version: 1.0.0
---

# Web Search Guidance

This session has a **web_search** MCP tool available via the SearXNG proxy.

## Using the tool

```
web_search(query="<your query>", num_results=10)
```

- **query**: Natural-language or keyword search query.
- **num_results**: How many results to return (1–20). Default 10.

## Engine availability

The default engine is **bing**. Other engines (duckduckgo, brave, startpage) may
return zero results due to rate-limiting or CAPTCHAs in many deployments.
If you receive empty results, do not switch engines — retry with a refined query.

## Effective queries

- Be specific: prefer "Go context cancellation pattern 2025" over "Go context"
- Use quotes for exact phrases: "content-length framing mcp stdio"
- Add site: for authoritative sources: "site:pkg.go.dev context.WithTimeout"
- Combine terms: "SearXNG docker compose arm64 2024"

## Reading results

Each result has:
- **Title** — page heading
- **URL** — source link (cite this in your answer)
- **Snippet** — up to 400 chars of page content

Always cite your sources. If a snippet is truncated, note that further detail
is at the URL.
