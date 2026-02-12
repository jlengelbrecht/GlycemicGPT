/**
 * Shared types for AI provider subprocess wrappers.
 */

/** OpenAI-compatible chat message */
export interface ChatMessage {
  role: "system" | "user" | "assistant";
  content: string;
}

/** OpenAI-compatible chat completion request */
export interface ChatCompletionRequest {
  model?: string;
  messages: ChatMessage[];
  stream?: boolean;
  temperature?: number;
  max_tokens?: number;
}

/** OpenAI-compatible non-streaming response */
export interface ChatCompletionResponse {
  id: string;
  object: "chat.completion";
  created: number;
  model: string;
  choices: Array<{
    index: number;
    message: { role: "assistant"; content: string };
    finish_reason: "stop" | "length";
  }>;
  usage: {
    prompt_tokens: number;
    completion_tokens: number;
    total_tokens: number;
  };
}

/** OpenAI-compatible streaming chunk */
export interface ChatCompletionChunk {
  id: string;
  object: "chat.completion.chunk";
  created: number;
  model: string;
  choices: Array<{
    index: number;
    delta: { role?: "assistant"; content?: string };
    finish_reason: "stop" | null;
  }>;
}

/** Result from a non-streaming provider call */
export interface ProviderResult {
  content: string;
  model: string;
}

/** Provider authentication state */
export interface ProviderAuthState {
  authenticated: boolean;
  provider: "claude" | "codex";
  /** Human-readable status message */
  message: string;
}

/** Abstract interface that each provider must implement */
export interface AIProvider {
  /** Check if this provider is authenticated and ready */
  checkAuth(): Promise<ProviderAuthState>;

  /** Run a non-streaming chat completion */
  complete(
    messages: ChatMessage[],
    model?: string,
  ): Promise<ProviderResult>;

  /** Run a streaming chat completion, calling onChunk for each text delta */
  stream(
    messages: ChatMessage[],
    model?: string,
    onChunk?: (text: string) => void,
  ): Promise<ProviderResult>;
}
