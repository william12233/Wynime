package com.wynime.app.domain.mediasource.test

import com.wynime.app.data.repository.RepositoryException

interface RefreshResult {

    interface Success : RefreshResult

    interface InProgress : RefreshResult

    sealed interface Failed : RefreshResult

    interface ApiError : Failed {
        val exception: RepositoryException
    }

    interface InvalidConfig : Failed

    interface UnknownError : Failed {
        val exception: Throwable
    }
}
