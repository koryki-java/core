# Function-doc introductions

Manually-authored introductions for the generated function-category pages in
`docs/functions/`. Each intro is inserted right after the page's `# <Title>` heading and
before the first function.

The generated pages carry a "do not edit" note — edit the intro **here**, in its own file,
not in the generated output.

## How to add an introduction

Create a file named `<category>.md` in this directory, where `<category>` is the lowercase
`FunctionCategory` name — the same as the generated page's filename:

| generated page                       | intro file                  |
|--------------------------------------|-----------------------------|
| `docs/functions/string.md`           | `intro/string.md`           |
| `docs/functions/datetime.md`         | `intro/datetime.md`         |
| `docs/functions/math.md`             | `intro/math.md`             |
| `docs/functions/pattern_matching.md` | `intro/pattern_matching.md` |

The file content is Markdown, inserted verbatim. A category **without** an intro file is
left unchanged, so you can add them one at a time.

### Available category filenames

`aggregate` · `arithmetic` · `comparison` · `conditional` · `conversion` · `datetime` ·
`formatting` · `logical` · `math` · `other` · `pattern_matching` · `string` · `window`

(`README.md` is ignored — only files whose name matches a category are used.)

## Regenerate the pages

After adding or editing an intro, regenerate `docs/functions/*.md` so they pick it up:

```sh
./gradlew :tools:test -Ddocs.write=true
```

Then review the diff and commit both the intro file and the regenerated pages.
