package com.wynime.datasources.bangumi.auth

interface Authentication {

    fun apply(query: MutableMap<String, List<String>>, headers: MutableMap<String, String>)

}
