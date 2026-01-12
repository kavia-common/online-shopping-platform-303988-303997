#!/bin/bash
cd /home/kavia/workspace/code-generation/online-shopping-platform-303988-303997/kotlin_frontend
./gradlew lint
LINT_EXIT_CODE=$?
if [ $LINT_EXIT_CODE -ne 0 ]; then
   exit 1
fi

