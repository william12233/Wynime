package com.wynime.cloud.auth

interface Authentication {

    fun apply(query: MutableMap<String, List<String>>, headers: MutableMap<String, String>)

}
