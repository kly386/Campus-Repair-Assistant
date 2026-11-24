"""把拆分 SQL 合并成新机器一键初始化脚本 docs/sql/setup-all.sql。

维护规则：
- 平时只改 docs/sql/ 下的拆分 SQL；
- 改完后运行：python scripts/build-setup-all.py；
- 不要直接改 setup-all.sql，下次生成会覆盖。
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SQL_DIR = ROOT / "docs" / "sql"
OUT = SQL_DIR / "setup-all.sql"

FILES = [
    "init.sql",
    "auth-rbac.sql",
    "auth-seed.sql",
    "dashboard-demo.sql",
    "file-object-patch.sql",
    "ai-usage.sql",
    "knowledge-patch.sql",
    "knowledge-review-menu.sql",
    "dispatch-patch.sql",
    "knowledge-seed.sql",
]

HEADER = """-- ============================================================
-- 校园快修服务助手：新机器一键初始化脚本
--
-- 本文件由 scripts/build-setup-all.py 生成，请勿直接编辑。
-- 需要改数据库脚本时，请改 docs/sql/ 下的拆分文件后重新生成。
--
-- 用法（仓库根目录，必须显式 utf8mb4）：
--   mysql -uroot -p --default-character-set=utf8mb4 < docs/sql/setup-all.sql
--   Get-Content -Raw -Encoding UTF8 docs/sql/setup-all.sql | mysql -uroot -p --default-character-set=utf8mb4
--
-- 本脚本会自动创建 campus_repair 数据库，并按依赖顺序导入：
--   1. init.sql                  基础业务表 + 初始类别/配置
--   2. auth-rbac.sql             认证与 RBAC 表
--   3. auth-seed.sql             角色/权限/演示账号
--   4. dashboard-demo.sql        看板演示数据（答辩用；不需要可删除第 4 段）
--   5. file-object-patch.sql     文件元数据表
--   6. ai-usage.sql              AI 调用统计表
--   7. knowledge-patch.sql       知识库管理权限
--   8. knowledge-review-menu.sql 知识库审核菜单
--   9. dispatch-patch.sql        调度权限与演示排班
--  10. knowledge-seed.sql       100 条自助诊断知识种子
--
-- 说明：原拆分 SQL 文件仍保留，便于单独维护；新机器只需要执行本文件一次。
-- 安全：本文件不包含任何 API key。
-- ============================================================

CREATE DATABASE IF NOT EXISTS `campus_repair`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `campus_repair`;
SET NAMES utf8mb4;

"""


def main() -> None:
    parts = [HEADER]
    total = len(FILES)
    for idx, name in enumerate(FILES, start=1):
        content = (SQL_DIR / name).read_text(encoding="utf-8").strip()
        parts.append(
            f"-- ------------------------------------------------------------\n"
            f"-- [{idx:02d}/{total:02d}] docs/sql/{name}\n"
            f"-- ------------------------------------------------------------\n"
            f"{content}\n\n"
        )
    OUT.write_text("".join(parts).rstrip() + "\n", encoding="utf-8", newline="\n")
    print(f"generated {OUT.relative_to(ROOT)} from {total} source files")


if __name__ == "__main__":
    main()
