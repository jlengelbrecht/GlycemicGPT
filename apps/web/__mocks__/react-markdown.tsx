import React from "react";

function ReactMarkdown({
  children,
}: {
  children: string;
  remarkPlugins?: unknown[];
  components?: Record<string, unknown>;
  [key: string]: unknown;
}) {
  return <div data-testid="markdown-content">{children}</div>;
}

export default ReactMarkdown;
