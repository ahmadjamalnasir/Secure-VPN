"""Initial schema: users and servers.

Captures the schema that was previously created by
`Base.metadata.create_all()`. Existing databases should be marked as already
at this revision rather than re-running it:

    alembic stamp 0001

Revision ID: 0001
Revises:
"""
from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0001"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "users",
        sa.Column("id", sa.String(), nullable=False),
        sa.Column("email", sa.String(), nullable=True),
        sa.Column("hashed_password", sa.String(), nullable=True),
        sa.Column("is_premium", sa.Boolean(), nullable=True),
        sa.Column("is_admin", sa.Boolean(), nullable=True),
        sa.Column("subscription_expiry", sa.DateTime(), nullable=True),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_users_id", "users", ["id"])
    op.create_index("ix_users_email", "users", ["email"], unique=True)

    op.create_table(
        "servers",
        sa.Column("id", sa.String(), nullable=False),
        sa.Column("country", sa.String(), nullable=True),
        sa.Column("city", sa.String(), nullable=True),
        sa.Column("ip_address", sa.String(), nullable=True),
        sa.Column("is_premium", sa.Boolean(), nullable=True),
        sa.Column("status", sa.String(), nullable=True),
        sa.Column("load_percent", sa.Integer(), nullable=True),
        sa.Column("wg_public_key", sa.String(), nullable=True),
        sa.Column("wg_endpoint", sa.String(), nullable=True),
        sa.Column("dns", sa.String(), nullable=True),
        sa.Column("keepalive", sa.Integer(), nullable=True),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_servers_id", "servers", ["id"])


def downgrade() -> None:
    op.drop_index("ix_servers_id", table_name="servers")
    op.drop_table("servers")
    op.drop_index("ix_users_email", table_name="users")
    op.drop_index("ix_users_id", table_name="users")
    op.drop_table("users")
