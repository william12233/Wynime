#
# Copyright (C) 2024 OpenAni and contributors.
#
# 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
# Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
#
# https://github.com/open-ani/ani/blob/main/LICENSE
#

export ANIMEKO_DESKTOP_TEST_TASK="download-update-and-install"
export ANIMEKO_DESKTOP_TEST_ARGC=1
export ANIMEKO_DESKTOP_TEST_ARGV_0="https://github.com/william12233/Wynime/releases/download/0.1/wynime-0.1-windows-x86_64.zip"
./Wynime.exe
read -p "Press enter to continue"
