"""Fields backing backoffice user, subscription and server management.

Revision ID: 0002
Revises: 0001
"""
from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0002"
down_revision: str | None = "0001"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

_NOW = sa.text("CURRENT_TIMESTAMP")


def upgrade() -> None:
    # server_default backfills existing rows; the model supplies the value for
    # new rows, so the default is dropped afterwards where it is not wanted.
    op.add_column("users", sa.Column("subscription_plan", sa.String(), nullable=True))
    op.add_column(
        "users",
        sa.Column("is_active", sa.Boolean(), nullable=False,
                  server_default=sa.true()),
    )
    op.add_column(
        "users",
        sa.Column("created_at", sa.DateTime(), nullable=False, server_default=_NOW),
    )

    op.add_column(
        "servers",
        sa.Column("created_at", sa.DateTime(), nullable=False, server_default=_NOW),
    )
    op.add_column(
        "servers",
        sa.Column("updated_at", sa.DateTime(), nullable=False, server_default=_NOW),
    )


def downgrade() -> None:
    op.drop_column("servers", "updated_at")
    op.drop_column("servers", "created_at")
    op.drop_column("users", "created_at")
    op.drop_column("users", "is_active")
    op.drop_column("users", "subscription_plan")
