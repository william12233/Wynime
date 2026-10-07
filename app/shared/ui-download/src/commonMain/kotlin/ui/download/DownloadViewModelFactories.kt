package com.wynime.app.ui.download

import com.wynime.app.ui.download.subject.SubjectDownloadsPresenterFactory
import com.wynime.app.ui.download.subject.SubjectDownloadsViewModel
import org.koin.core.Koin
import org.koin.mp.KoinPlatform

fun createDownloadManagementViewModel(): DownloadManagementViewModel {
    val koin = KoinPlatform.getKoin()
    return DownloadManagementViewModel(koin.get(), koin.get(), koin.get(), koin.get(), subjectDownloadsPresenterFactory(koin))
}

fun createSubjectDownloadsViewModel(subjectId: Int): SubjectDownloadsViewModel {
    val koin = KoinPlatform.getKoin()
    return SubjectDownloadsViewModel(subjectId, subjectDownloadsPresenterFactory(koin))
}

private fun subjectDownloadsPresenterFactory(koin: Koin) = SubjectDownloadsPresenterFactory(
    koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get(),
)
