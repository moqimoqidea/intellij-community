#!/usr/bin/env bash

echoerr() {
  local message=$*
  echo -e "\033[0;31m$message\033[0m" 1>&2
}

echowarn() {
  local message=$*
  echo -e "\033[1;33m$message\033[0m" 1>&2
}

# Function to post a comment to the PR and exit.
# It uses the gh CLI, which is pre-installed on GitHub-hosted runners.
# The GITHUB_TOKEN and PR_NUMBER are provided by the workflow.
fail_check() {
  local message=$1
  echoerr "$message"

  if [[ -n "$PR_NUMBER" && -n "$GITHUB_TOKEN" ]]; then
    if ! gh pr comment "$PR_NUMBER" --body "$message"; then
      echowarn "Failed to post comment to PR #$PR_NUMBER. Continuing..."
    fi
  else
    echowarn "PR_NUMBER or GITHUB_TOKEN not set, skipping posting PR comment."
  fi
}
