# AltCheck

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-3cb043)](#requirements)
[![Loader](https://img.shields.io/badge/Fabric%20Loader-0.16.14-orange)](#requirements)
[![Fabric API](https://img.shields.io/badge/Fabric%20API-0.116.10%2B1.21.1-blue)](#requirements)
[![Java](https://img.shields.io/badge/Java-21-red)](#requirements)
[![License](https://img.shields.io/badge/License-GNU%20LICENSE-lightgrey)](LICENSE.txt)

AltCheck is a Fabric server moderation utility that helps detect alternate accounts by tracking shared IP usage, player history, and session behavior.

*Coded because the one I was using had IPs in plaintext in a player_ips.txt file...*

## Features

- Track player login IP usage over time
- Detect potential alts via shared IP relationships
- View grouped IP links and player history
- Session tracking (join/leave durations, online/offline status)
- Risk scoring (VPN + behavioral signal blend)
- Permission-based command access
- IP masking in chat with optional hover/click reveal (`altcheck.view.ip`)
- Admin utilities for hide/purge/cleanup/statistics

---

## Commands

### Core

- `/altcheck trace <player>` - Trace linked accounts for a player
- `/altcheck lookup <ip>` - Find players associated with an IP
- `/altcheck history <player>` - Show player IP history
- `/altcheck list` - Show grouped shared-IP relationships

### Investigation (`inv`)

- `/altcheck alts <player>` - Show alternate accounts
- `/altcheck ip <player>` - Show known IPs for a player
- `/altcheck sessions <player> [page]` - Show session history (paged)
- `/altcheck score <player>` - Show risk score breakdown

### Admin

- `/altcheck purge <player>` - Delete a player's stored data
- `/altcheck hide <player>` - Toggle player visibility in results
- `/altcheck cleanup` - Remove old records
- `/altcheck stats` - Show database statistics

### Help

- `/altcheck help` - Show commands you can access
- `/altcheck help core`
- `/altcheck help inv`
- `/altcheck help admin`

---

## Permissions

> Notes:
> - Access is controlled by Fabric Permissions API.
> - Command visibility in help is permission-aware.

| Permission | Description | Suggested Level |
|---|---|-----------------|
| `altcheck.use` | Base access to root command | `2`             |
| `altcheck.trace` | Use trace command | `2`             |
| `altcheck.lookup` | Use lookup command | `4`             |
| `altcheck.history` | Use history command | `4`             |
| `altcheck.list` | Use list command | `4`             |
| `altcheck.alts` | Use alts command | `2`             |
| `altcheck.ip` | Use ip command | `4`             |
| `altcheck.sessions` | Use sessions command | `4`             |
| `altcheck.score` | Use score command | `2`             |
| `altcheck.purge` | Use purge command | `4`             |
| `altcheck.hide` | Use hide command | `4`             |
| `altcheck.cleanup` | Use cleanup command | `4`             |
| `altcheck.stats` | Use stats command | `2`             |
| `altcheck.view.ip` | View hover/click full IP instead of masked-only | `4`             |

---

## Setup

### Requirements

- Java `21`
- minecraft_version `1.21.1`
- yarn_mappings `1.21.1+build.3`
- loader_version `0.16.14`
- fabric_version `0.116.10+1.21.1`
- fabric_permission_api_version `0.3.1`
- Fabric Permissions API (`me.lucko.fabric.api.permissions.v0.Permissions`)
