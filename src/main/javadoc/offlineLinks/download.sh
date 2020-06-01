#!/bin/bash
#
# aoserv-jilter - Mail filter for the AOServ Platform.
# Copyright (C) 2020  AO Industries, Inc.
#     support@aoindustries.com
#     7262 Bull Pen Cir
#     Mobile, AL 36695
#
# This file is part of aoserv-jilter.
#
# aoserv-jilter is free software: you can redistribute it and/or modify
# it under the terms of the GNU Lesser General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# aoserv-jilter is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public License
# along with aoserv-jilter.  If not, see <http://www.gnu.org/licenses/>.
#

set -e
cd "${BASH_SOURCE%/*}"

rm com.sendmail/jilter/* -rf
curl -fsS -o com.sendmail/jilter/package-list http://sendmail-jilter.sourceforge.net/apidocs/package-list
