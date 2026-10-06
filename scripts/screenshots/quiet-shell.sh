#!/bin/bash
# tmux default shell for the screenshot demo daemon ($SHELL in ios-screenshots.yml):
# no prompt and no echo, so the launch command the daemon types into each
# session's pane (`'<script>' '<task>' '<dir>'; echo …`) never shows up in
# session previews or alert bodies.
stty -echo 2>/dev/null
export PS1='' PS2=''
exec /bin/bash --noprofile --norc --noediting -i
