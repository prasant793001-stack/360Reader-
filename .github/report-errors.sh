#!/usr/bin/env bash
# Turns the important lines of a failed Gradle build into GitHub annotations,
# so the reason for a failure is visible right on the Actions run page.
log="$1"
lines=$(grep -E "^e: |error:|What went wrong|Caused by|Could not|Exception|FAILED|> " "$log" | grep -v "^> Task .*UP-TO-DATE" | head -n 60)
[ -n "$lines" ] || lines=$(tail -n 40 "$log")
# Annotations are capped per step, so send them in a few chunks.
printf '%s\n' "$lines" | split -l 15 - /tmp/err_chunk_
for f in /tmp/err_chunk_*; do
  msg=$(sed -e 's/%/%25/g' "$f" | awk 'BEGIN{ORS="%0A"} {print}')
  echo "::error title=Build error::$msg"
done
