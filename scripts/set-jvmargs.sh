#!/usr/bin/env bash
 
                                                   
 
                                                                             
                                                                                                          
 
                                                   
 

                                                                   

set -euo pipefail

new_val="$1"
prop_file="${2:-gradle.properties}"

                                                                            
if grep -q '^org\.gradle\.jvmargs=' "$prop_file"; then
  sed -i.bak -E "s|^org\.gradle\.jvmargs=.*|org.gradle.jvmargs=${new_val}|" "$prop_file"
else
  echo "org.gradle.jvmargs=${new_val}" >> "$prop_file"
fi
