"""Story 35.9: Knowledge retrieval service for RAG system.

Retrieves relevant clinical knowledge chunks from the vector database
using semantic similarity search with trust-tier filtering.
"""

import uuid

from sqlalchemy import or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from src.logging_config import get_logger
from src.models.knowledge_chunk import KnowledgeChunk
from src.services.embedding import embed_text

logger = get_logger(__name__)

# Default number of chunks to retrieve per query
DEFAULT_TOP_K = 5

# Minimum similarity score to include (cosine distance; lower = more similar)
MAX_COSINE_DISTANCE = 0.6


async def retrieve_knowledge(
    db: AsyncSession,
    user_id: uuid.UUID,
    query: str,
    top_k: int = DEFAULT_TOP_K,
) -> list[KnowledgeChunk]:
    """Retrieve relevant knowledge chunks for a user query.

    Uses pgvector cosine similarity to find the most relevant chunks.
    Filters by:
    - User-scoped (user's own + shared system chunks)
    - Currently valid (valid_to IS NULL)
    - Excludes injection-flagged chunks from untrusted sources

    Args:
        db: Database session.
        user_id: User's UUID.
        query: The user's question text.
        top_k: Maximum chunks to return.

    Returns:
        List of relevant KnowledgeChunk objects, ordered by relevance.
    """
    # Embed the query
    try:
        query_embedding = embed_text(query)
    except Exception:
        logger.warning(
            "Failed to embed query for knowledge retrieval",
            user_id=str(user_id),
            exc_info=True,
        )
        return []

    # Vector similarity search with filters
    try:
        result = await db.execute(
            select(KnowledgeChunk)
            .where(
                # User-scoped: shared (NULL) + user-specific
                or_(
                    KnowledgeChunk.user_id == user_id,
                    KnowledgeChunk.user_id.is_(None),
                ),
                # Only currently valid chunks
                KnowledgeChunk.valid_to.is_(None),
                # Exclude injection-flagged chunks from untrusted sources
                or_(
                    KnowledgeChunk.injection_risk.is_(False),
                    KnowledgeChunk.trust_tier == "AUTHORITATIVE",
                ),
                # Must have an embedding
                KnowledgeChunk.embedding.is_not(None),
            )
            .order_by(KnowledgeChunk.embedding.cosine_distance(query_embedding))
            .limit(top_k)
        )
        chunks = list(result.scalars().all())
    except Exception:
        logger.warning(
            "Knowledge retrieval query failed",
            user_id=str(user_id),
            exc_info=True,
        )
        return []

    logger.debug(
        "Knowledge chunks retrieved",
        user_id=str(user_id),
        chunks_found=len(chunks),
        top_k=top_k,
    )

    return chunks


def format_knowledge_for_prompt(chunks: list[KnowledgeChunk]) -> str | None:
    """Format retrieved knowledge chunks as a text block for AI prompts.

    Each chunk is labeled with its trust tier and source for attribution.

    Args:
        chunks: List of KnowledgeChunk objects from retrieval.

    Returns:
        Formatted text block, or None if no chunks.
    """
    if not chunks:
        return None

    lines = ["[Clinical Knowledge (retrieved)]"]

    for chunk in chunks:
        tier_label = chunk.trust_tier.upper()
        source = chunk.source_name or chunk.source_type
        lines.append(f"[{tier_label} - {source}]")
        lines.append(chunk.content.strip())
        lines.append("")

    return "\n".join(lines).rstrip()
