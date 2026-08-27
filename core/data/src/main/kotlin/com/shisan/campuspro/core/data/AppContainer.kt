package com.shisan.campuspro.core.data

class AppContainer {
    val authRepository: AuthRepository = InMemoryAuthRepository()
    val scheduleRepository: ScheduleRepository = InMemoryScheduleRepository()
    val gradesRepository: GradesRepository = InMemoryGradesRepository()
    val examsRepository: ExamsRepository = InMemoryExamsRepository()
    val syncRepository: SyncRepository = DefaultSyncRepository(
        scheduleRepository = scheduleRepository,
        gradesRepository = gradesRepository,
        examsRepository = examsRepository,
    )
}
