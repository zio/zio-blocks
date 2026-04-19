#!/usr/bin/env python3
"""
Minimal MCP (Model Context Protocol) server for extract-methods tool.
Implements JSON-RPC over stdio without external dependencies.
"""

import json
import subprocess
import sys
from pathlib import Path

def main():
    """Run the MCP server."""
    try:
        while True:
            line = sys.stdin.readline()
            if not line:
                break

            request = json.loads(line)
            response = handle_request(request)
            if response:
                sys.stdout.write(json.dumps(response) + "\n")
                sys.stdout.flush()
    except (json.JSONDecodeError, EOFError, BrokenPipeError):
        pass
    except Exception as e:
        sys.stderr.write(f"Error: {e}\n")
        sys.stderr.flush()

def handle_request(request: dict) -> dict:
    """Handle an incoming JSON-RPC request."""
    jsonrpc = request.get("jsonrpc", "2.0")
    method = request.get("method")
    params = request.get("params", {})
    req_id = request.get("id")

    # Initialize/capabilities
    if method == "initialize":
        return {
            "jsonrpc": jsonrpc,
            "id": req_id,
            "result": {
                "protocolVersion": "2024-11-05",
                "capabilities": {
                    "tools": {}
                },
                "serverInfo": {
                    "name": "zio-blocks-tools",
                    "version": "1.0.0"
                }
            }
        }

    # List tools
    if method == "tools/list":
        return {
            "jsonrpc": jsonrpc,
            "id": req_id,
            "result": {
                "tools": [
                    {
                        "name": "extract_methods",
                        "description": "Extract public method names from a Scala source file",
                        "inputSchema": {
                            "type": "object",
                            "properties": {
                                "source_file": {
                                    "type": "string",
                                    "description": "Path to the .scala source file"
                                },
                                "type_name": {
                                    "type": "string",
                                    "description": "Optional class/object name to scope extraction"
                                }
                            },
                            "required": ["source_file"]
                        }
                    }
                ]
            }
        }

    # Call tool
    if method == "tools/call":
        tool_name = params.get("name")
        tool_params = params.get("arguments", {})

        if tool_name == "extract_methods":
            source_file = tool_params.get("source_file", "")
            type_name = tool_params.get("type_name", "")

            try:
                args = ["scala", ".claude/skills/docs-data-type-ref/extract-methods.scala", source_file]
                if type_name:
                    args.append(type_name)

                result = subprocess.run(args, capture_output=True, text=True, timeout=30)
                output = result.stdout.strip() or "(no public methods found)"

                if result.returncode not in (0, 2):
                    output = f"Error: {result.stderr}"

                return {
                    "jsonrpc": jsonrpc,
                    "id": req_id,
                    "result": {
                        "content": [
                            {
                                "type": "text",
                                "text": output
                            }
                        ]
                    }
                }
            except subprocess.TimeoutExpired:
                return {
                    "jsonrpc": jsonrpc,
                    "id": req_id,
                    "error": {
                        "code": -32603,
                        "message": "Tool execution timed out"
                    }
                }
            except Exception as e:
                return {
                    "jsonrpc": jsonrpc,
                    "id": req_id,
                    "error": {
                        "code": -32603,
                        "message": str(e)
                    }
                }
        else:
            return {
                "jsonrpc": jsonrpc,
                "id": req_id,
                "error": {
                    "code": -32601,
                    "message": f"Unknown tool: {tool_name}"
                }
            }

    # Unknown method
    return {
        "jsonrpc": jsonrpc,
        "id": req_id,
        "error": {
            "code": -32601,
            "message": f"Unknown method: {method}"
        }
    }

if __name__ == "__main__":
    main()
