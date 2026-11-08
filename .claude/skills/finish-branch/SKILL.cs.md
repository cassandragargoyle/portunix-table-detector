---
language: cs
translation-of: SKILL.md
warning: Český překlad pro lidi. Závazná je anglická verze SKILL.md; při jakémkoli rozporu má přednost anglická verze.
---

# Dokončení feature branch

Dokončení práce na feature branch — kontrola README, merge do hlavní větve, úklid branch
a volitelná aktualizace stavu issue.

@.claude/prompts/assistant-programming.md

**DŮLEŽITÉ: NIKDY nepřidávej "Generated with Claude Code" ani trailer "Co-Authored-By:" (Claude
ani jiného AI agenta) do kódových souborů, git commitů ani popisů pull requestů.**

## Instrukce

### KROK 1: Zjisti kontext

Zeptej se na (nebo převezmi z aktuálního stavu):

1. Název feature branch (výchozí: aktuální branch)
2. Cílovou větev pro merge (výchozí: `main`)

Ověř, že branch existuje, obsahuje commity a pracovní strom je čistý:

```bash
git status --short
git log --oneline <cílová-větev>..HEAD
```

Číslo issue `N` vezmi z názvu branch (`feature/<N>-...`) nebo ze scope commitů. Pokud jsou
necommitnuté změny, **STOP** a zeptej se, zda je nejdřív commitnout.

### KROK 2: Aktualizace README a plugin.json

Před mergem ověř, že dokumentace odpovídá stavu po změně:

```bash
git diff --name-only <cílová-větev>...HEAD
```

1. **`README.md`**:
   - nové nebo změněné CLI volby příkazu `detect` jsou v sekci použití a v příkladech,
   - seznam podporovaných formátů, backendů a funkcí odpovídá skutečnosti,
   - poznámky k závislostem (token pro GitHub Packages) a omezením nejsou zastaralé,
   - Maven snippet závislosti ukazuje poslední vydanou verzi.
2. **`plugin.json`**:
   - `commands[].options` odpovídají Picocli volbám v `table-detector-core`,
   - `description` a `ai_integration` popisují, co plugin dělá dnes.
3. **Verze ve shodě** — všechny čtyři `pom.xml` (`X.Y.Z-SNAPSHOT`) a `plugin.json`
   (`version` = `X.Y.Z`, `plugin.binary` = `table-detector-X.Y.Z.jar`):

   ```bash
   grep -n -m1 '<version>' pom.xml
   grep -n -A1 '<artifactId>table-detector-parent</artifactId>' */pom.xml | grep version
   grep -n -e '"version"' -e '"binary"' plugin.json
   ```

4. **Architektonické dokumenty** — každý změněný zdrojový soubor má v branch aktualizovaný
   odpovídající `.md`.

Pokud něco nesedí, oprav to a commitni do feature branch ještě před mergem:

```text
docs(<N>): Update README and plugin.json
```

Pokud vše sedí, krok přeskoč a uveď to ve shrnutí.

### KROK 3: Ověření buildu

```bash
mvn clean verify
```

Pokud selže, **STOP** a nahlas výstup.

### KROK 4: Merge a úklid

> **Co je PR (Pull Request)?** Žádost o začlenění změn z feature branch do hlavní větve. Slouží
> k code review v týmu a spouští workflow `Build`. Při sólo vývoji je možný přímý merge.

Zeptej se uživatele na preferovaný postup.

#### Možnost A: Pull Request (výchozí v projektu, viz `AGENTS.md`)

Připrav popis PR:

- **Summary**: Co PR dělá
- **Motivation**: Proč (odkaz na issue)
- **Changes**: Seznam změn
- **Testing**: Kroky k ověření

```bash
git push -u origin <branch>
gh pr create --base <cílová-větev> --title "<type>(<N>): <Summary>" --body "..."
```

`Closes #<N>` v těle PR uzavře GitHub issue při mergi. Merguj jen na pokyn uživatele
(`gh pr merge <PR> --squash --delete-branch` nebo jím zvolená metoda), potom:

```bash
git switch <cílová-větev> && git pull --ff-only
git branch -d <branch>
```

#### Možnost B: Přímý merge (sólo vývoj)

```bash
git switch <cílová-větev>
git pull --ff-only
git merge <branch>
git push origin <cílová-větev>
git branch -d <branch>
git push origin --delete <branch>   # jen pokud byla branch pushnutá
```

**STOP** - Počkej na potvrzení před mergem a před pushem do `main`.

### KROK 5: Aktualizace issue (volitelné)

Zeptej se uživatele: „Je tímto issue kompletně dokončené?“

#### Pokud ANO

Pokud existuje detailní soubor v `docs/issues/`, uprav ho:

- Změň `**Status**: 📋 Open` na `**Status**: ✅ Implemented`
- Doplň datum uzavření (`**Closed**: YYYY-MM-DD`)

Přesuň soubor do archivu a uzavři GitHub issue (pokud ho už neuzavřel PR) — žádné tabulky
v README nejsou, seznam žije na GitHubu:

```bash
git mv docs/issues/<NNN>-*.md docs/issues/done/
gh issue close <N>
```

Commitni změnu stavu issue (na feature branch před mergem, nebo na `main` po něm):

```text
docs(<N>): Close issue - implementation complete
```

#### Pokud NE

Aktualizaci stavu přeskoč. Issue zůstává otevřené pro další práci.

### KROK 6: Shrnutí

Zobraz shrnutí:

- README / plugin.json aktualizováno: ano/ne (nebylo potřeba)
- Build: prošel
- Merge proveden do větve: `<název>` (URL PR nebo přímý merge)
- Feature branch smazána: ano/ne
- Stav issue aktualizován: ano/ne/přeskočeno
- Další kroky (pokud jsou, např. `/bump-version` a release tag)
