package dev.sadeghtahani.rhinochat.chat.data.local

/** The fake server can reuse server IDs after its in-memory state is reset. */
class MessageIdentityConflictException(conversationId: String, serverId: String) :
    IllegalStateException("Conflicting message identity in conversation $conversationId for server ID $serverId")
