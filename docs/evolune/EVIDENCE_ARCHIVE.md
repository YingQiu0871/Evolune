# 证据归档说明

2026-10-03 起，各版本目录下的 `evidence/` 子目录（原始验证证据：哈希清单、测试 XML、日志、dex 导出、截图等，约 63 MB、4,714 个文件）已从主分支移除，以缩小仓库检出体积。没有改写 git 历史，这些文件仍完整保存在提交 [`337cfbe`](https://github.com/YingQiu0871/Evolune/commit/337cfbe73f8dad3da1fc47fdb3545d67e5425610) 及之前的历史中。

发布说明、契约、计划和阶段报告等 Markdown 文档仍留在原处；文中出现的 `evidence/...` 路径都指向下表归档位置中的同名文件。

| 版本 | 文件数 | 归档位置 |
|---|---:|---|
| v1.7 | 3618 | [v1.7/evidence/](https://github.com/YingQiu0871/Evolune/tree/337cfbe73f8dad3da1fc47fdb3545d67e5425610/docs/evolune/v1.7/evidence) |
| v1.7.1 | 73 | [v1.7.1/evidence/](https://github.com/YingQiu0871/Evolune/tree/337cfbe73f8dad3da1fc47fdb3545d67e5425610/docs/evolune/v1.7.1/evidence) |
| v1.7.2 | 211 | [v1.7.2/evidence/](https://github.com/YingQiu0871/Evolune/tree/337cfbe73f8dad3da1fc47fdb3545d67e5425610/docs/evolune/v1.7.2/evidence) |
| v1.7.3 | 57 | [v1.7.3/evidence/](https://github.com/YingQiu0871/Evolune/tree/337cfbe73f8dad3da1fc47fdb3545d67e5425610/docs/evolune/v1.7.3/evidence) |
| v1.7.4 | 66 | [v1.7.4/evidence/](https://github.com/YingQiu0871/Evolune/tree/337cfbe73f8dad3da1fc47fdb3545d67e5425610/docs/evolune/v1.7.4/evidence) |
| v1.8.0 | 510 | [v1.8.0/evidence/](https://github.com/YingQiu0871/Evolune/tree/337cfbe73f8dad3da1fc47fdb3545d67e5425610/docs/evolune/v1.8.0/evidence) |
| v1.9.0 | 179 | [v1.9.0/evidence/](https://github.com/YingQiu0871/Evolune/tree/337cfbe73f8dad3da1fc47fdb3545d67e5425610/docs/evolune/v1.9.0/evidence) |

## 本地找回

```sh
# 查看单个文件
git show 337cfbe73f8dad3da1fc47fdb3545d67e5425610:docs/evolune/v1.7/evidence/c-01/MANIFEST.sha256

# 把某个版本的证据目录恢复到工作区（不要提交回主分支）
git restore --source=337cfbe73f8dad3da1fc47fdb3545d67e5425610 -- docs/evolune/v1.7/evidence
```

浅克隆需要先 `git fetch --unshallow` 或 `git fetch origin 337cfbe73f8dad3da1fc47fdb3545d67e5425610`。

## 今后的新证据

新版本的原始证据（大批测试 XML、日志、dex 导出、截图）不再提交到主分支，建议作为 GitHub Release 附件或 PR 附件保存；仓库内只保留结论性的 Markdown 摘要和必要的哈希。
