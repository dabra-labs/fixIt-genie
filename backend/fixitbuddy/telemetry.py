"""Structured telemetry callbacks for FixIt Genie live sessions."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
import json
import logging
import threading
import time
from typing import Any

from google.adk.agents.context import Context as CallbackContext
from google.adk.models.llm_request import LlmRequest
from google.adk.models.llm_response import LlmResponse
from google.adk.tools.base_tool import BaseTool

logger = logging.getLogger(__name__)

_MAX_ITEMS = 6
_MAX_TEXT = 180

_lock = threading.Lock()
_active_invocations: dict[str, "InvocationMetrics"] = {}
_active_model_calls: dict[str, int] = {}
_active_tool_calls: dict[str, int] = {}


@dataclass
class InvocationMetrics:
    turn_index: int
    started_ns: int
    model_calls: int = 0
    model_duration_ms: int = 0
    tool_calls: int = 0
    tool_duration_ms: int = 0
    tool_names: list[str] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def _truncate_text(value: str) -> str:
    if len(value) <= _MAX_TEXT:
        return value
    return value[: _MAX_TEXT - 3] + "..."


def _summarize_value(value: Any, depth: int = 0) -> Any:
    if depth >= 3:
        return "<max-depth>"
    if value is None or isinstance(value, (bool, int, float)):
        return value
    if isinstance(value, str):
        return _truncate_text(value)
    if isinstance(value, bytes):
        return {"type": "bytes", "len": len(value)}
    if isinstance(value, dict):
        items: dict[str, Any] = {}
        for index, (key, item) in enumerate(value.items()):
            if index >= _MAX_ITEMS:
                items["..."] = f"{len(value) - _MAX_ITEMS} more"
                break
            items[str(key)] = _summarize_value(item, depth + 1)
        return items
    if isinstance(value, (list, tuple, set)):
        seq = list(value)
        items = [_summarize_value(item, depth + 1) for item in seq[:_MAX_ITEMS]]
        if len(seq) > _MAX_ITEMS:
            items.append(f"... {len(seq) - _MAX_ITEMS} more")
        return items
    if hasattr(value, "model_dump"):
        try:
            return _summarize_value(value.model_dump(exclude_none=True), depth + 1)
        except TypeError:
            return _summarize_value(value.model_dump(), depth + 1)
    if hasattr(value, "text") and isinstance(getattr(value, "text"), str):
        return _truncate_text(getattr(value, "text"))
    return _truncate_text(repr(value))


def _extract_text_from_content(content: Any) -> list[str]:
    if content is None or not getattr(content, "parts", None):
        return []
    texts: list[str] = []
    for part in content.parts[:_MAX_ITEMS]:
        text = getattr(part, "text", None)
        if text:
            texts.append(_truncate_text(text))
    return texts


def _log_event(event_type: str, context: CallbackContext, **fields: Any) -> None:
    payload = {
        "kind": "fixitgenie_telemetry",
        "event": event_type,
        "ts": _now_iso(),
        "agent_name": context.agent_name,
        "invocation_id": context.invocation_id,
        "session_id": context.session.id,
        "user_id": context.user_id,
    }
    payload.update(fields)
    logger.info("TELEMETRY %s", json.dumps(payload, sort_keys=True, default=str))


def _tool_call_key(tool_context: CallbackContext, tool_name: str) -> str:
    return tool_context.function_call_id or f"{tool_context.invocation_id}:{tool_name}"


def _ensure_session_started(context: CallbackContext) -> None:
    if context.state.get("telemetry:session_started"):
        return
    context.state["telemetry:session_started"] = True
    _log_event("session_start", context)


def before_agent_callback(callback_context: CallbackContext) -> None:
    _ensure_session_started(callback_context)
    turn_index = int(callback_context.state.get("telemetry:turn_index", 0)) + 1
    callback_context.state["telemetry:turn_index"] = turn_index
    metrics = InvocationMetrics(turn_index=turn_index, started_ns=time.perf_counter_ns())
    with _lock:
        _active_invocations[callback_context.invocation_id] = metrics
    _log_event(
        "agent_turn_start",
        callback_context,
        turn_index=turn_index,
        user_input_summary=_extract_text_from_content(callback_context.user_content),
    )
    return None


def after_agent_callback(callback_context: CallbackContext) -> None:
    with _lock:
        metrics = _active_invocations.pop(callback_context.invocation_id, None)
        _active_model_calls.pop(callback_context.invocation_id, None)
    if metrics is None:
        return None
    duration_ms = (time.perf_counter_ns() - metrics.started_ns) // 1_000_000
    _log_event(
        "agent_turn_end",
        callback_context,
        turn_index=metrics.turn_index,
        duration_ms=duration_ms,
        model_calls=metrics.model_calls,
        model_duration_ms=metrics.model_duration_ms,
        tool_calls=metrics.tool_calls,
        tool_duration_ms=metrics.tool_duration_ms,
        tools_used=metrics.tool_names,
        errors=metrics.errors,
    )
    return None


def before_model_callback(
    callback_context: CallbackContext,
    llm_request: LlmRequest,
) -> None:
    with _lock:
        _active_model_calls[callback_context.invocation_id] = time.perf_counter_ns()
    _log_event(
        "model_start",
        callback_context,
        model=llm_request.model,
        content_count=len(llm_request.contents),
        tool_count=len(llm_request.tools_dict),
    )
    return None


def after_model_callback(
    callback_context: CallbackContext,
    llm_response: LlmResponse,
) -> None:
    with _lock:
        started_ns = _active_model_calls.pop(callback_context.invocation_id, None)
        metrics = _active_invocations.get(callback_context.invocation_id)
    duration_ms = 0
    if started_ns is not None:
        duration_ms = (time.perf_counter_ns() - started_ns) // 1_000_000
    if metrics is not None:
        metrics.model_calls += 1
        metrics.model_duration_ms += duration_ms
    _log_event(
        "model_end",
        callback_context,
        duration_ms=duration_ms,
        partial=llm_response.partial,
        turn_complete=llm_response.turn_complete,
        interrupted=llm_response.interrupted,
        finish_reason=str(llm_response.finish_reason) if llm_response.finish_reason else None,
        model_version=llm_response.model_version,
        output_text_summary=_extract_text_from_content(llm_response.content),
    )
    return None


def on_model_error_callback(
    callback_context: CallbackContext,
    llm_request: LlmRequest,
    error: Exception,
) -> None:
    with _lock:
        started_ns = _active_model_calls.pop(callback_context.invocation_id, None)
        metrics = _active_invocations.get(callback_context.invocation_id)
    duration_ms = 0
    if started_ns is not None:
        duration_ms = (time.perf_counter_ns() - started_ns) // 1_000_000
    if metrics is not None:
        metrics.errors.append(f"model:{type(error).__name__}")
    _log_event(
        "model_error",
        callback_context,
        duration_ms=duration_ms,
        model=llm_request.model,
        error_type=type(error).__name__,
        error_message=_truncate_text(str(error)),
    )
    return None


def before_tool_callback(
    tool: BaseTool,
    args: dict[str, Any],
    tool_context: CallbackContext,
) -> None:
    key = _tool_call_key(tool_context, tool.name)
    with _lock:
        _active_tool_calls[key] = time.perf_counter_ns()
    _log_event(
        "tool_start",
        tool_context,
        tool_name=tool.name,
        function_call_id=tool_context.function_call_id,
        args_summary=_summarize_value(args),
    )
    return None


def after_tool_callback(
    tool: BaseTool,
    args: dict[str, Any],
    tool_context: CallbackContext,
    tool_response: Any,
) -> None:
    key = _tool_call_key(tool_context, tool.name)
    with _lock:
        started_ns = _active_tool_calls.pop(key, None)
        metrics = _active_invocations.get(tool_context.invocation_id)
    duration_ms = 0
    if started_ns is not None:
        duration_ms = (time.perf_counter_ns() - started_ns) // 1_000_000
    if metrics is not None:
        metrics.tool_calls += 1
        metrics.tool_duration_ms += duration_ms
        metrics.tool_names.append(tool.name)
    _log_event(
        "tool_end",
        tool_context,
        tool_name=tool.name,
        function_call_id=tool_context.function_call_id,
        duration_ms=duration_ms,
        args_summary=_summarize_value(args),
        response_summary=_summarize_value(tool_response),
    )
    return None


def on_tool_error_callback(
    tool: BaseTool,
    args: dict[str, Any],
    tool_context: CallbackContext,
    error: Exception,
) -> None:
    key = _tool_call_key(tool_context, tool.name)
    with _lock:
        started_ns = _active_tool_calls.pop(key, None)
        metrics = _active_invocations.get(tool_context.invocation_id)
    duration_ms = 0
    if started_ns is not None:
        duration_ms = (time.perf_counter_ns() - started_ns) // 1_000_000
    if metrics is not None:
        metrics.errors.append(f"{tool.name}:{type(error).__name__}")
    _log_event(
        "tool_error",
        tool_context,
        tool_name=tool.name,
        function_call_id=tool_context.function_call_id,
        duration_ms=duration_ms,
        args_summary=_summarize_value(args),
        error_type=type(error).__name__,
        error_message=_truncate_text(str(error)),
    )
    return None
