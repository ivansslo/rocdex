import type { IncomingMessage, ServerResponse } from 'node:http'
import { handleUnifiedResponsesProxyRequest } from './unifiedResponsesProxy.js'

const OPENAI_RESPONSES_ENDPOINT = 'https://api.openai.com/v1/responses'
const OPENAI_CHAT_COMPLETIONS_ENDPOINT = 'https://api.openai.com/v1/chat/completions'

export function handleOpenAiProxyRequest(
  req: IncomingMessage,
  res: ServerResponse,
  bearerToken: string,
  wireApi: 'responses' | 'chat',
): void {
  handleUnifiedResponsesProxyRequest(req, res, {
    bearerToken,
    wireApi,
    responsesEndpoint: OPENAI_RESPONSES_ENDPOINT,
    chatCompletionsEndpoint: OPENAI_CHAT_COMPLETIONS_ENDPOINT,
    missingKeyMessage: 'Missing OpenAI API key',
    allowToolFallbackToResponses: true,
  })
}
