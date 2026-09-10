// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'wynime_database.dart';

// ignore_for_file: type=lint
class $AppSettingsRowsTable extends AppSettingsRows
    with TableInfo<$AppSettingsRowsTable, SettingsRecord> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $AppSettingsRowsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _singletonIdMeta = const VerificationMeta(
    'singletonId',
  );
  @override
  late final GeneratedColumn<int> singletonId = GeneratedColumn<int>(
    'singleton_id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  static const VerificationMeta _themeMeta = const VerificationMeta('theme');
  @override
  late final GeneratedColumn<String> theme = GeneratedColumn<String>(
    'theme',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _interfaceLanguageMeta = const VerificationMeta(
    'interfaceLanguage',
  );
  @override
  late final GeneratedColumn<String> interfaceLanguage =
      GeneratedColumn<String>(
        'interface_language',
        aliasedName,
        false,
        type: DriftSqlType.string,
        requiredDuringInsert: true,
      );
  static const VerificationMeta _telemetryEnabledMeta = const VerificationMeta(
    'telemetryEnabled',
  );
  @override
  late final GeneratedColumn<bool> telemetryEnabled = GeneratedColumn<bool>(
    'telemetry_enabled',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("telemetry_enabled" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  @override
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
    'updated_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [
    singletonId,
    theme,
    interfaceLanguage,
    telemetryEnabled,
    updatedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'app_settings_rows';
  @override
  VerificationContext validateIntegrity(
    Insertable<SettingsRecord> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('singleton_id')) {
      context.handle(
        _singletonIdMeta,
        singletonId.isAcceptableOrUnknown(
          data['singleton_id']!,
          _singletonIdMeta,
        ),
      );
    }
    if (data.containsKey('theme')) {
      context.handle(
        _themeMeta,
        theme.isAcceptableOrUnknown(data['theme']!, _themeMeta),
      );
    } else if (isInserting) {
      context.missing(_themeMeta);
    }
    if (data.containsKey('interface_language')) {
      context.handle(
        _interfaceLanguageMeta,
        interfaceLanguage.isAcceptableOrUnknown(
          data['interface_language']!,
          _interfaceLanguageMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_interfaceLanguageMeta);
    }
    if (data.containsKey('telemetry_enabled')) {
      context.handle(
        _telemetryEnabledMeta,
        telemetryEnabled.isAcceptableOrUnknown(
          data['telemetry_enabled']!,
          _telemetryEnabledMeta,
        ),
      );
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_updatedAtMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {singletonId};
  @override
  SettingsRecord map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return SettingsRecord(
      singletonId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}singleton_id'],
      )!,
      theme: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}theme'],
      )!,
      interfaceLanguage: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}interface_language'],
      )!,
      telemetryEnabled: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}telemetry_enabled'],
      )!,
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      )!,
    );
  }

  @override
  $AppSettingsRowsTable createAlias(String alias) {
    return $AppSettingsRowsTable(attachedDatabase, alias);
  }
}

class SettingsRecord extends DataClass implements Insertable<SettingsRecord> {
  final int singletonId;
  final String theme;
  final String interfaceLanguage;
  final bool telemetryEnabled;
  final DateTime updatedAt;
  const SettingsRecord({
    required this.singletonId,
    required this.theme,
    required this.interfaceLanguage,
    required this.telemetryEnabled,
    required this.updatedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['singleton_id'] = Variable<int>(singletonId);
    map['theme'] = Variable<String>(theme);
    map['interface_language'] = Variable<String>(interfaceLanguage);
    map['telemetry_enabled'] = Variable<bool>(telemetryEnabled);
    map['updated_at'] = Variable<DateTime>(updatedAt);
    return map;
  }

  AppSettingsRowsCompanion toCompanion(bool nullToAbsent) {
    return AppSettingsRowsCompanion(
      singletonId: Value(singletonId),
      theme: Value(theme),
      interfaceLanguage: Value(interfaceLanguage),
      telemetryEnabled: Value(telemetryEnabled),
      updatedAt: Value(updatedAt),
    );
  }

  factory SettingsRecord.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return SettingsRecord(
      singletonId: serializer.fromJson<int>(json['singletonId']),
      theme: serializer.fromJson<String>(json['theme']),
      interfaceLanguage: serializer.fromJson<String>(json['interfaceLanguage']),
      telemetryEnabled: serializer.fromJson<bool>(json['telemetryEnabled']),
      updatedAt: serializer.fromJson<DateTime>(json['updatedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'singletonId': serializer.toJson<int>(singletonId),
      'theme': serializer.toJson<String>(theme),
      'interfaceLanguage': serializer.toJson<String>(interfaceLanguage),
      'telemetryEnabled': serializer.toJson<bool>(telemetryEnabled),
      'updatedAt': serializer.toJson<DateTime>(updatedAt),
    };
  }

  SettingsRecord copyWith({
    int? singletonId,
    String? theme,
    String? interfaceLanguage,
    bool? telemetryEnabled,
    DateTime? updatedAt,
  }) => SettingsRecord(
    singletonId: singletonId ?? this.singletonId,
    theme: theme ?? this.theme,
    interfaceLanguage: interfaceLanguage ?? this.interfaceLanguage,
    telemetryEnabled: telemetryEnabled ?? this.telemetryEnabled,
    updatedAt: updatedAt ?? this.updatedAt,
  );
  SettingsRecord copyWithCompanion(AppSettingsRowsCompanion data) {
    return SettingsRecord(
      singletonId: data.singletonId.present
          ? data.singletonId.value
          : this.singletonId,
      theme: data.theme.present ? data.theme.value : this.theme,
      interfaceLanguage: data.interfaceLanguage.present
          ? data.interfaceLanguage.value
          : this.interfaceLanguage,
      telemetryEnabled: data.telemetryEnabled.present
          ? data.telemetryEnabled.value
          : this.telemetryEnabled,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('SettingsRecord(')
          ..write('singletonId: $singletonId, ')
          ..write('theme: $theme, ')
          ..write('interfaceLanguage: $interfaceLanguage, ')
          ..write('telemetryEnabled: $telemetryEnabled, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    singletonId,
    theme,
    interfaceLanguage,
    telemetryEnabled,
    updatedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is SettingsRecord &&
          other.singletonId == this.singletonId &&
          other.theme == this.theme &&
          other.interfaceLanguage == this.interfaceLanguage &&
          other.telemetryEnabled == this.telemetryEnabled &&
          other.updatedAt == this.updatedAt);
}

class AppSettingsRowsCompanion extends UpdateCompanion<SettingsRecord> {
  final Value<int> singletonId;
  final Value<String> theme;
  final Value<String> interfaceLanguage;
  final Value<bool> telemetryEnabled;
  final Value<DateTime> updatedAt;
  const AppSettingsRowsCompanion({
    this.singletonId = const Value.absent(),
    this.theme = const Value.absent(),
    this.interfaceLanguage = const Value.absent(),
    this.telemetryEnabled = const Value.absent(),
    this.updatedAt = const Value.absent(),
  });
  AppSettingsRowsCompanion.insert({
    this.singletonId = const Value.absent(),
    required String theme,
    required String interfaceLanguage,
    this.telemetryEnabled = const Value.absent(),
    required DateTime updatedAt,
  }) : theme = Value(theme),
       interfaceLanguage = Value(interfaceLanguage),
       updatedAt = Value(updatedAt);
  static Insertable<SettingsRecord> custom({
    Expression<int>? singletonId,
    Expression<String>? theme,
    Expression<String>? interfaceLanguage,
    Expression<bool>? telemetryEnabled,
    Expression<DateTime>? updatedAt,
  }) {
    return RawValuesInsertable({
      if (singletonId != null) 'singleton_id': singletonId,
      if (theme != null) 'theme': theme,
      if (interfaceLanguage != null) 'interface_language': interfaceLanguage,
      if (telemetryEnabled != null) 'telemetry_enabled': telemetryEnabled,
      if (updatedAt != null) 'updated_at': updatedAt,
    });
  }

  AppSettingsRowsCompanion copyWith({
    Value<int>? singletonId,
    Value<String>? theme,
    Value<String>? interfaceLanguage,
    Value<bool>? telemetryEnabled,
    Value<DateTime>? updatedAt,
  }) {
    return AppSettingsRowsCompanion(
      singletonId: singletonId ?? this.singletonId,
      theme: theme ?? this.theme,
      interfaceLanguage: interfaceLanguage ?? this.interfaceLanguage,
      telemetryEnabled: telemetryEnabled ?? this.telemetryEnabled,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (singletonId.present) {
      map['singleton_id'] = Variable<int>(singletonId.value);
    }
    if (theme.present) {
      map['theme'] = Variable<String>(theme.value);
    }
    if (interfaceLanguage.present) {
      map['interface_language'] = Variable<String>(interfaceLanguage.value);
    }
    if (telemetryEnabled.present) {
      map['telemetry_enabled'] = Variable<bool>(telemetryEnabled.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('AppSettingsRowsCompanion(')
          ..write('singletonId: $singletonId, ')
          ..write('theme: $theme, ')
          ..write('interfaceLanguage: $interfaceLanguage, ')
          ..write('telemetryEnabled: $telemetryEnabled, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }
}

class $WatchHistoryRowsTable extends WatchHistoryRows
    with TableInfo<$WatchHistoryRowsTable, WatchHistoryRecord> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $WatchHistoryRowsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _progressIdMeta = const VerificationMeta(
    'progressId',
  );
  @override
  late final GeneratedColumn<String> progressId = GeneratedColumn<String>(
    'progress_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _sourceIdMeta = const VerificationMeta(
    'sourceId',
  );
  @override
  late final GeneratedColumn<String> sourceId = GeneratedColumn<String>(
    'source_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _lineIdMeta = const VerificationMeta('lineId');
  @override
  late final GeneratedColumn<String> lineId = GeneratedColumn<String>(
    'line_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _subjectIdMeta = const VerificationMeta(
    'subjectId',
  );
  @override
  late final GeneratedColumn<String> subjectId = GeneratedColumn<String>(
    'subject_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _episodeIdMeta = const VerificationMeta(
    'episodeId',
  );
  @override
  late final GeneratedColumn<String> episodeId = GeneratedColumn<String>(
    'episode_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _positionMsMeta = const VerificationMeta(
    'positionMs',
  );
  @override
  late final GeneratedColumn<int> positionMs = GeneratedColumn<int>(
    'position_ms',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _durationMsMeta = const VerificationMeta(
    'durationMs',
  );
  @override
  late final GeneratedColumn<int> durationMs = GeneratedColumn<int>(
    'duration_ms',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _isCompletedMeta = const VerificationMeta(
    'isCompleted',
  );
  @override
  late final GeneratedColumn<bool> isCompleted = GeneratedColumn<bool>(
    'is_completed',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("is_completed" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _playerBackendIdMeta = const VerificationMeta(
    'playerBackendId',
  );
  @override
  late final GeneratedColumn<String> playerBackendId = GeneratedColumn<String>(
    'player_backend_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _timelineMapIdMeta = const VerificationMeta(
    'timelineMapId',
  );
  @override
  late final GeneratedColumn<String> timelineMapId = GeneratedColumn<String>(
    'timeline_map_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  @override
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
    'updated_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [
    progressId,
    sourceId,
    lineId,
    subjectId,
    episodeId,
    positionMs,
    durationMs,
    isCompleted,
    playerBackendId,
    timelineMapId,
    updatedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'watch_history_rows';
  @override
  VerificationContext validateIntegrity(
    Insertable<WatchHistoryRecord> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('progress_id')) {
      context.handle(
        _progressIdMeta,
        progressId.isAcceptableOrUnknown(data['progress_id']!, _progressIdMeta),
      );
    } else if (isInserting) {
      context.missing(_progressIdMeta);
    }
    if (data.containsKey('source_id')) {
      context.handle(
        _sourceIdMeta,
        sourceId.isAcceptableOrUnknown(data['source_id']!, _sourceIdMeta),
      );
    } else if (isInserting) {
      context.missing(_sourceIdMeta);
    }
    if (data.containsKey('line_id')) {
      context.handle(
        _lineIdMeta,
        lineId.isAcceptableOrUnknown(data['line_id']!, _lineIdMeta),
      );
    } else if (isInserting) {
      context.missing(_lineIdMeta);
    }
    if (data.containsKey('subject_id')) {
      context.handle(
        _subjectIdMeta,
        subjectId.isAcceptableOrUnknown(data['subject_id']!, _subjectIdMeta),
      );
    } else if (isInserting) {
      context.missing(_subjectIdMeta);
    }
    if (data.containsKey('episode_id')) {
      context.handle(
        _episodeIdMeta,
        episodeId.isAcceptableOrUnknown(data['episode_id']!, _episodeIdMeta),
      );
    } else if (isInserting) {
      context.missing(_episodeIdMeta);
    }
    if (data.containsKey('position_ms')) {
      context.handle(
        _positionMsMeta,
        positionMs.isAcceptableOrUnknown(data['position_ms']!, _positionMsMeta),
      );
    } else if (isInserting) {
      context.missing(_positionMsMeta);
    }
    if (data.containsKey('duration_ms')) {
      context.handle(
        _durationMsMeta,
        durationMs.isAcceptableOrUnknown(data['duration_ms']!, _durationMsMeta),
      );
    } else if (isInserting) {
      context.missing(_durationMsMeta);
    }
    if (data.containsKey('is_completed')) {
      context.handle(
        _isCompletedMeta,
        isCompleted.isAcceptableOrUnknown(
          data['is_completed']!,
          _isCompletedMeta,
        ),
      );
    }
    if (data.containsKey('player_backend_id')) {
      context.handle(
        _playerBackendIdMeta,
        playerBackendId.isAcceptableOrUnknown(
          data['player_backend_id']!,
          _playerBackendIdMeta,
        ),
      );
    }
    if (data.containsKey('timeline_map_id')) {
      context.handle(
        _timelineMapIdMeta,
        timelineMapId.isAcceptableOrUnknown(
          data['timeline_map_id']!,
          _timelineMapIdMeta,
        ),
      );
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_updatedAtMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {progressId};
  @override
  List<Set<GeneratedColumn>> get uniqueKeys => [
    {sourceId, lineId, subjectId, episodeId},
  ];
  @override
  WatchHistoryRecord map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return WatchHistoryRecord(
      progressId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}progress_id'],
      )!,
      sourceId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}source_id'],
      )!,
      lineId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}line_id'],
      )!,
      subjectId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}subject_id'],
      )!,
      episodeId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}episode_id'],
      )!,
      positionMs: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}position_ms'],
      )!,
      durationMs: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}duration_ms'],
      )!,
      isCompleted: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}is_completed'],
      )!,
      playerBackendId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}player_backend_id'],
      ),
      timelineMapId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}timeline_map_id'],
      ),
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      )!,
    );
  }

  @override
  $WatchHistoryRowsTable createAlias(String alias) {
    return $WatchHistoryRowsTable(attachedDatabase, alias);
  }
}

class WatchHistoryRecord extends DataClass
    implements Insertable<WatchHistoryRecord> {
  final String progressId;
  final String sourceId;
  final String lineId;
  final String subjectId;
  final String episodeId;
  final int positionMs;
  final int durationMs;
  final bool isCompleted;
  final String? playerBackendId;
  final String? timelineMapId;
  final DateTime updatedAt;
  const WatchHistoryRecord({
    required this.progressId,
    required this.sourceId,
    required this.lineId,
    required this.subjectId,
    required this.episodeId,
    required this.positionMs,
    required this.durationMs,
    required this.isCompleted,
    this.playerBackendId,
    this.timelineMapId,
    required this.updatedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['progress_id'] = Variable<String>(progressId);
    map['source_id'] = Variable<String>(sourceId);
    map['line_id'] = Variable<String>(lineId);
    map['subject_id'] = Variable<String>(subjectId);
    map['episode_id'] = Variable<String>(episodeId);
    map['position_ms'] = Variable<int>(positionMs);
    map['duration_ms'] = Variable<int>(durationMs);
    map['is_completed'] = Variable<bool>(isCompleted);
    if (!nullToAbsent || playerBackendId != null) {
      map['player_backend_id'] = Variable<String>(playerBackendId);
    }
    if (!nullToAbsent || timelineMapId != null) {
      map['timeline_map_id'] = Variable<String>(timelineMapId);
    }
    map['updated_at'] = Variable<DateTime>(updatedAt);
    return map;
  }

  WatchHistoryRowsCompanion toCompanion(bool nullToAbsent) {
    return WatchHistoryRowsCompanion(
      progressId: Value(progressId),
      sourceId: Value(sourceId),
      lineId: Value(lineId),
      subjectId: Value(subjectId),
      episodeId: Value(episodeId),
      positionMs: Value(positionMs),
      durationMs: Value(durationMs),
      isCompleted: Value(isCompleted),
      playerBackendId: playerBackendId == null && nullToAbsent
          ? const Value.absent()
          : Value(playerBackendId),
      timelineMapId: timelineMapId == null && nullToAbsent
          ? const Value.absent()
          : Value(timelineMapId),
      updatedAt: Value(updatedAt),
    );
  }

  factory WatchHistoryRecord.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return WatchHistoryRecord(
      progressId: serializer.fromJson<String>(json['progressId']),
      sourceId: serializer.fromJson<String>(json['sourceId']),
      lineId: serializer.fromJson<String>(json['lineId']),
      subjectId: serializer.fromJson<String>(json['subjectId']),
      episodeId: serializer.fromJson<String>(json['episodeId']),
      positionMs: serializer.fromJson<int>(json['positionMs']),
      durationMs: serializer.fromJson<int>(json['durationMs']),
      isCompleted: serializer.fromJson<bool>(json['isCompleted']),
      playerBackendId: serializer.fromJson<String?>(json['playerBackendId']),
      timelineMapId: serializer.fromJson<String?>(json['timelineMapId']),
      updatedAt: serializer.fromJson<DateTime>(json['updatedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'progressId': serializer.toJson<String>(progressId),
      'sourceId': serializer.toJson<String>(sourceId),
      'lineId': serializer.toJson<String>(lineId),
      'subjectId': serializer.toJson<String>(subjectId),
      'episodeId': serializer.toJson<String>(episodeId),
      'positionMs': serializer.toJson<int>(positionMs),
      'durationMs': serializer.toJson<int>(durationMs),
      'isCompleted': serializer.toJson<bool>(isCompleted),
      'playerBackendId': serializer.toJson<String?>(playerBackendId),
      'timelineMapId': serializer.toJson<String?>(timelineMapId),
      'updatedAt': serializer.toJson<DateTime>(updatedAt),
    };
  }

  WatchHistoryRecord copyWith({
    String? progressId,
    String? sourceId,
    String? lineId,
    String? subjectId,
    String? episodeId,
    int? positionMs,
    int? durationMs,
    bool? isCompleted,
    Value<String?> playerBackendId = const Value.absent(),
    Value<String?> timelineMapId = const Value.absent(),
    DateTime? updatedAt,
  }) => WatchHistoryRecord(
    progressId: progressId ?? this.progressId,
    sourceId: sourceId ?? this.sourceId,
    lineId: lineId ?? this.lineId,
    subjectId: subjectId ?? this.subjectId,
    episodeId: episodeId ?? this.episodeId,
    positionMs: positionMs ?? this.positionMs,
    durationMs: durationMs ?? this.durationMs,
    isCompleted: isCompleted ?? this.isCompleted,
    playerBackendId: playerBackendId.present
        ? playerBackendId.value
        : this.playerBackendId,
    timelineMapId: timelineMapId.present
        ? timelineMapId.value
        : this.timelineMapId,
    updatedAt: updatedAt ?? this.updatedAt,
  );
  WatchHistoryRecord copyWithCompanion(WatchHistoryRowsCompanion data) {
    return WatchHistoryRecord(
      progressId: data.progressId.present
          ? data.progressId.value
          : this.progressId,
      sourceId: data.sourceId.present ? data.sourceId.value : this.sourceId,
      lineId: data.lineId.present ? data.lineId.value : this.lineId,
      subjectId: data.subjectId.present ? data.subjectId.value : this.subjectId,
      episodeId: data.episodeId.present ? data.episodeId.value : this.episodeId,
      positionMs: data.positionMs.present
          ? data.positionMs.value
          : this.positionMs,
      durationMs: data.durationMs.present
          ? data.durationMs.value
          : this.durationMs,
      isCompleted: data.isCompleted.present
          ? data.isCompleted.value
          : this.isCompleted,
      playerBackendId: data.playerBackendId.present
          ? data.playerBackendId.value
          : this.playerBackendId,
      timelineMapId: data.timelineMapId.present
          ? data.timelineMapId.value
          : this.timelineMapId,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('WatchHistoryRecord(')
          ..write('progressId: $progressId, ')
          ..write('sourceId: $sourceId, ')
          ..write('lineId: $lineId, ')
          ..write('subjectId: $subjectId, ')
          ..write('episodeId: $episodeId, ')
          ..write('positionMs: $positionMs, ')
          ..write('durationMs: $durationMs, ')
          ..write('isCompleted: $isCompleted, ')
          ..write('playerBackendId: $playerBackendId, ')
          ..write('timelineMapId: $timelineMapId, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    progressId,
    sourceId,
    lineId,
    subjectId,
    episodeId,
    positionMs,
    durationMs,
    isCompleted,
    playerBackendId,
    timelineMapId,
    updatedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is WatchHistoryRecord &&
          other.progressId == this.progressId &&
          other.sourceId == this.sourceId &&
          other.lineId == this.lineId &&
          other.subjectId == this.subjectId &&
          other.episodeId == this.episodeId &&
          other.positionMs == this.positionMs &&
          other.durationMs == this.durationMs &&
          other.isCompleted == this.isCompleted &&
          other.playerBackendId == this.playerBackendId &&
          other.timelineMapId == this.timelineMapId &&
          other.updatedAt == this.updatedAt);
}

class WatchHistoryRowsCompanion extends UpdateCompanion<WatchHistoryRecord> {
  final Value<String> progressId;
  final Value<String> sourceId;
  final Value<String> lineId;
  final Value<String> subjectId;
  final Value<String> episodeId;
  final Value<int> positionMs;
  final Value<int> durationMs;
  final Value<bool> isCompleted;
  final Value<String?> playerBackendId;
  final Value<String?> timelineMapId;
  final Value<DateTime> updatedAt;
  final Value<int> rowid;
  const WatchHistoryRowsCompanion({
    this.progressId = const Value.absent(),
    this.sourceId = const Value.absent(),
    this.lineId = const Value.absent(),
    this.subjectId = const Value.absent(),
    this.episodeId = const Value.absent(),
    this.positionMs = const Value.absent(),
    this.durationMs = const Value.absent(),
    this.isCompleted = const Value.absent(),
    this.playerBackendId = const Value.absent(),
    this.timelineMapId = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  WatchHistoryRowsCompanion.insert({
    required String progressId,
    required String sourceId,
    required String lineId,
    required String subjectId,
    required String episodeId,
    required int positionMs,
    required int durationMs,
    this.isCompleted = const Value.absent(),
    this.playerBackendId = const Value.absent(),
    this.timelineMapId = const Value.absent(),
    required DateTime updatedAt,
    this.rowid = const Value.absent(),
  }) : progressId = Value(progressId),
       sourceId = Value(sourceId),
       lineId = Value(lineId),
       subjectId = Value(subjectId),
       episodeId = Value(episodeId),
       positionMs = Value(positionMs),
       durationMs = Value(durationMs),
       updatedAt = Value(updatedAt);
  static Insertable<WatchHistoryRecord> custom({
    Expression<String>? progressId,
    Expression<String>? sourceId,
    Expression<String>? lineId,
    Expression<String>? subjectId,
    Expression<String>? episodeId,
    Expression<int>? positionMs,
    Expression<int>? durationMs,
    Expression<bool>? isCompleted,
    Expression<String>? playerBackendId,
    Expression<String>? timelineMapId,
    Expression<DateTime>? updatedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (progressId != null) 'progress_id': progressId,
      if (sourceId != null) 'source_id': sourceId,
      if (lineId != null) 'line_id': lineId,
      if (subjectId != null) 'subject_id': subjectId,
      if (episodeId != null) 'episode_id': episodeId,
      if (positionMs != null) 'position_ms': positionMs,
      if (durationMs != null) 'duration_ms': durationMs,
      if (isCompleted != null) 'is_completed': isCompleted,
      if (playerBackendId != null) 'player_backend_id': playerBackendId,
      if (timelineMapId != null) 'timeline_map_id': timelineMapId,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  WatchHistoryRowsCompanion copyWith({
    Value<String>? progressId,
    Value<String>? sourceId,
    Value<String>? lineId,
    Value<String>? subjectId,
    Value<String>? episodeId,
    Value<int>? positionMs,
    Value<int>? durationMs,
    Value<bool>? isCompleted,
    Value<String?>? playerBackendId,
    Value<String?>? timelineMapId,
    Value<DateTime>? updatedAt,
    Value<int>? rowid,
  }) {
    return WatchHistoryRowsCompanion(
      progressId: progressId ?? this.progressId,
      sourceId: sourceId ?? this.sourceId,
      lineId: lineId ?? this.lineId,
      subjectId: subjectId ?? this.subjectId,
      episodeId: episodeId ?? this.episodeId,
      positionMs: positionMs ?? this.positionMs,
      durationMs: durationMs ?? this.durationMs,
      isCompleted: isCompleted ?? this.isCompleted,
      playerBackendId: playerBackendId ?? this.playerBackendId,
      timelineMapId: timelineMapId ?? this.timelineMapId,
      updatedAt: updatedAt ?? this.updatedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (progressId.present) {
      map['progress_id'] = Variable<String>(progressId.value);
    }
    if (sourceId.present) {
      map['source_id'] = Variable<String>(sourceId.value);
    }
    if (lineId.present) {
      map['line_id'] = Variable<String>(lineId.value);
    }
    if (subjectId.present) {
      map['subject_id'] = Variable<String>(subjectId.value);
    }
    if (episodeId.present) {
      map['episode_id'] = Variable<String>(episodeId.value);
    }
    if (positionMs.present) {
      map['position_ms'] = Variable<int>(positionMs.value);
    }
    if (durationMs.present) {
      map['duration_ms'] = Variable<int>(durationMs.value);
    }
    if (isCompleted.present) {
      map['is_completed'] = Variable<bool>(isCompleted.value);
    }
    if (playerBackendId.present) {
      map['player_backend_id'] = Variable<String>(playerBackendId.value);
    }
    if (timelineMapId.present) {
      map['timeline_map_id'] = Variable<String>(timelineMapId.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('WatchHistoryRowsCompanion(')
          ..write('progressId: $progressId, ')
          ..write('sourceId: $sourceId, ')
          ..write('lineId: $lineId, ')
          ..write('subjectId: $subjectId, ')
          ..write('episodeId: $episodeId, ')
          ..write('positionMs: $positionMs, ')
          ..write('durationMs: $durationMs, ')
          ..write('isCompleted: $isCompleted, ')
          ..write('playerBackendId: $playerBackendId, ')
          ..write('timelineMapId: $timelineMapId, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $ArtifactManifestsTable extends ArtifactManifests
    with TableInfo<$ArtifactManifestsTable, ArtifactManifestRecord> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $ArtifactManifestsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _manifestIdMeta = const VerificationMeta(
    'manifestId',
  );
  @override
  late final GeneratedColumn<String> manifestId = GeneratedColumn<String>(
    'manifest_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _downloadIdMeta = const VerificationMeta(
    'downloadId',
  );
  @override
  late final GeneratedColumn<String> downloadId = GeneratedColumn<String>(
    'download_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways('UNIQUE'),
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [manifestId, downloadId, createdAt];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'artifact_manifests';
  @override
  VerificationContext validateIntegrity(
    Insertable<ArtifactManifestRecord> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('manifest_id')) {
      context.handle(
        _manifestIdMeta,
        manifestId.isAcceptableOrUnknown(data['manifest_id']!, _manifestIdMeta),
      );
    } else if (isInserting) {
      context.missing(_manifestIdMeta);
    }
    if (data.containsKey('download_id')) {
      context.handle(
        _downloadIdMeta,
        downloadId.isAcceptableOrUnknown(data['download_id']!, _downloadIdMeta),
      );
    } else if (isInserting) {
      context.missing(_downloadIdMeta);
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    } else if (isInserting) {
      context.missing(_createdAtMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {manifestId};
  @override
  ArtifactManifestRecord map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return ArtifactManifestRecord(
      manifestId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}manifest_id'],
      )!,
      downloadId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}download_id'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
    );
  }

  @override
  $ArtifactManifestsTable createAlias(String alias) {
    return $ArtifactManifestsTable(attachedDatabase, alias);
  }
}

class ArtifactManifestRecord extends DataClass
    implements Insertable<ArtifactManifestRecord> {
  final String manifestId;
  final String downloadId;
  final DateTime createdAt;
  const ArtifactManifestRecord({
    required this.manifestId,
    required this.downloadId,
    required this.createdAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['manifest_id'] = Variable<String>(manifestId);
    map['download_id'] = Variable<String>(downloadId);
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  ArtifactManifestsCompanion toCompanion(bool nullToAbsent) {
    return ArtifactManifestsCompanion(
      manifestId: Value(manifestId),
      downloadId: Value(downloadId),
      createdAt: Value(createdAt),
    );
  }

  factory ArtifactManifestRecord.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return ArtifactManifestRecord(
      manifestId: serializer.fromJson<String>(json['manifestId']),
      downloadId: serializer.fromJson<String>(json['downloadId']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'manifestId': serializer.toJson<String>(manifestId),
      'downloadId': serializer.toJson<String>(downloadId),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  ArtifactManifestRecord copyWith({
    String? manifestId,
    String? downloadId,
    DateTime? createdAt,
  }) => ArtifactManifestRecord(
    manifestId: manifestId ?? this.manifestId,
    downloadId: downloadId ?? this.downloadId,
    createdAt: createdAt ?? this.createdAt,
  );
  ArtifactManifestRecord copyWithCompanion(ArtifactManifestsCompanion data) {
    return ArtifactManifestRecord(
      manifestId: data.manifestId.present
          ? data.manifestId.value
          : this.manifestId,
      downloadId: data.downloadId.present
          ? data.downloadId.value
          : this.downloadId,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('ArtifactManifestRecord(')
          ..write('manifestId: $manifestId, ')
          ..write('downloadId: $downloadId, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(manifestId, downloadId, createdAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is ArtifactManifestRecord &&
          other.manifestId == this.manifestId &&
          other.downloadId == this.downloadId &&
          other.createdAt == this.createdAt);
}

class ArtifactManifestsCompanion
    extends UpdateCompanion<ArtifactManifestRecord> {
  final Value<String> manifestId;
  final Value<String> downloadId;
  final Value<DateTime> createdAt;
  final Value<int> rowid;
  const ArtifactManifestsCompanion({
    this.manifestId = const Value.absent(),
    this.downloadId = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  ArtifactManifestsCompanion.insert({
    required String manifestId,
    required String downloadId,
    required DateTime createdAt,
    this.rowid = const Value.absent(),
  }) : manifestId = Value(manifestId),
       downloadId = Value(downloadId),
       createdAt = Value(createdAt);
  static Insertable<ArtifactManifestRecord> custom({
    Expression<String>? manifestId,
    Expression<String>? downloadId,
    Expression<DateTime>? createdAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (manifestId != null) 'manifest_id': manifestId,
      if (downloadId != null) 'download_id': downloadId,
      if (createdAt != null) 'created_at': createdAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  ArtifactManifestsCompanion copyWith({
    Value<String>? manifestId,
    Value<String>? downloadId,
    Value<DateTime>? createdAt,
    Value<int>? rowid,
  }) {
    return ArtifactManifestsCompanion(
      manifestId: manifestId ?? this.manifestId,
      downloadId: downloadId ?? this.downloadId,
      createdAt: createdAt ?? this.createdAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (manifestId.present) {
      map['manifest_id'] = Variable<String>(manifestId.value);
    }
    if (downloadId.present) {
      map['download_id'] = Variable<String>(downloadId.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('ArtifactManifestsCompanion(')
          ..write('manifestId: $manifestId, ')
          ..write('downloadId: $downloadId, ')
          ..write('createdAt: $createdAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $ArtifactRowsTable extends ArtifactRows
    with TableInfo<$ArtifactRowsTable, ArtifactRecord> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $ArtifactRowsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _artifactIdMeta = const VerificationMeta(
    'artifactId',
  );
  @override
  late final GeneratedColumn<String> artifactId = GeneratedColumn<String>(
    'artifact_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _manifestIdMeta = const VerificationMeta(
    'manifestId',
  );
  @override
  late final GeneratedColumn<String> manifestId = GeneratedColumn<String>(
    'manifest_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES artifact_manifests (manifest_id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _kindMeta = const VerificationMeta('kind');
  @override
  late final GeneratedColumn<String> kind = GeneratedColumn<String>(
    'kind',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _fileUriMeta = const VerificationMeta(
    'fileUri',
  );
  @override
  late final GeneratedColumn<String> fileUri = GeneratedColumn<String>(
    'file_uri',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways('UNIQUE'),
  );
  @override
  List<GeneratedColumn> get $columns => [artifactId, manifestId, kind, fileUri];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'artifact_rows';
  @override
  VerificationContext validateIntegrity(
    Insertable<ArtifactRecord> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('artifact_id')) {
      context.handle(
        _artifactIdMeta,
        artifactId.isAcceptableOrUnknown(data['artifact_id']!, _artifactIdMeta),
      );
    } else if (isInserting) {
      context.missing(_artifactIdMeta);
    }
    if (data.containsKey('manifest_id')) {
      context.handle(
        _manifestIdMeta,
        manifestId.isAcceptableOrUnknown(data['manifest_id']!, _manifestIdMeta),
      );
    } else if (isInserting) {
      context.missing(_manifestIdMeta);
    }
    if (data.containsKey('kind')) {
      context.handle(
        _kindMeta,
        kind.isAcceptableOrUnknown(data['kind']!, _kindMeta),
      );
    } else if (isInserting) {
      context.missing(_kindMeta);
    }
    if (data.containsKey('file_uri')) {
      context.handle(
        _fileUriMeta,
        fileUri.isAcceptableOrUnknown(data['file_uri']!, _fileUriMeta),
      );
    } else if (isInserting) {
      context.missing(_fileUriMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {manifestId, artifactId};
  @override
  ArtifactRecord map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return ArtifactRecord(
      artifactId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}artifact_id'],
      )!,
      manifestId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}manifest_id'],
      )!,
      kind: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}kind'],
      )!,
      fileUri: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}file_uri'],
      )!,
    );
  }

  @override
  $ArtifactRowsTable createAlias(String alias) {
    return $ArtifactRowsTable(attachedDatabase, alias);
  }
}

class ArtifactRecord extends DataClass implements Insertable<ArtifactRecord> {
  final String artifactId;
  final String manifestId;
  final String kind;
  final String fileUri;
  const ArtifactRecord({
    required this.artifactId,
    required this.manifestId,
    required this.kind,
    required this.fileUri,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['artifact_id'] = Variable<String>(artifactId);
    map['manifest_id'] = Variable<String>(manifestId);
    map['kind'] = Variable<String>(kind);
    map['file_uri'] = Variable<String>(fileUri);
    return map;
  }

  ArtifactRowsCompanion toCompanion(bool nullToAbsent) {
    return ArtifactRowsCompanion(
      artifactId: Value(artifactId),
      manifestId: Value(manifestId),
      kind: Value(kind),
      fileUri: Value(fileUri),
    );
  }

  factory ArtifactRecord.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return ArtifactRecord(
      artifactId: serializer.fromJson<String>(json['artifactId']),
      manifestId: serializer.fromJson<String>(json['manifestId']),
      kind: serializer.fromJson<String>(json['kind']),
      fileUri: serializer.fromJson<String>(json['fileUri']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'artifactId': serializer.toJson<String>(artifactId),
      'manifestId': serializer.toJson<String>(manifestId),
      'kind': serializer.toJson<String>(kind),
      'fileUri': serializer.toJson<String>(fileUri),
    };
  }

  ArtifactRecord copyWith({
    String? artifactId,
    String? manifestId,
    String? kind,
    String? fileUri,
  }) => ArtifactRecord(
    artifactId: artifactId ?? this.artifactId,
    manifestId: manifestId ?? this.manifestId,
    kind: kind ?? this.kind,
    fileUri: fileUri ?? this.fileUri,
  );
  ArtifactRecord copyWithCompanion(ArtifactRowsCompanion data) {
    return ArtifactRecord(
      artifactId: data.artifactId.present
          ? data.artifactId.value
          : this.artifactId,
      manifestId: data.manifestId.present
          ? data.manifestId.value
          : this.manifestId,
      kind: data.kind.present ? data.kind.value : this.kind,
      fileUri: data.fileUri.present ? data.fileUri.value : this.fileUri,
    );
  }

  @override
  String toString() {
    return (StringBuffer('ArtifactRecord(')
          ..write('artifactId: $artifactId, ')
          ..write('manifestId: $manifestId, ')
          ..write('kind: $kind, ')
          ..write('fileUri: $fileUri')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(artifactId, manifestId, kind, fileUri);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is ArtifactRecord &&
          other.artifactId == this.artifactId &&
          other.manifestId == this.manifestId &&
          other.kind == this.kind &&
          other.fileUri == this.fileUri);
}

class ArtifactRowsCompanion extends UpdateCompanion<ArtifactRecord> {
  final Value<String> artifactId;
  final Value<String> manifestId;
  final Value<String> kind;
  final Value<String> fileUri;
  final Value<int> rowid;
  const ArtifactRowsCompanion({
    this.artifactId = const Value.absent(),
    this.manifestId = const Value.absent(),
    this.kind = const Value.absent(),
    this.fileUri = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  ArtifactRowsCompanion.insert({
    required String artifactId,
    required String manifestId,
    required String kind,
    required String fileUri,
    this.rowid = const Value.absent(),
  }) : artifactId = Value(artifactId),
       manifestId = Value(manifestId),
       kind = Value(kind),
       fileUri = Value(fileUri);
  static Insertable<ArtifactRecord> custom({
    Expression<String>? artifactId,
    Expression<String>? manifestId,
    Expression<String>? kind,
    Expression<String>? fileUri,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (artifactId != null) 'artifact_id': artifactId,
      if (manifestId != null) 'manifest_id': manifestId,
      if (kind != null) 'kind': kind,
      if (fileUri != null) 'file_uri': fileUri,
      if (rowid != null) 'rowid': rowid,
    });
  }

  ArtifactRowsCompanion copyWith({
    Value<String>? artifactId,
    Value<String>? manifestId,
    Value<String>? kind,
    Value<String>? fileUri,
    Value<int>? rowid,
  }) {
    return ArtifactRowsCompanion(
      artifactId: artifactId ?? this.artifactId,
      manifestId: manifestId ?? this.manifestId,
      kind: kind ?? this.kind,
      fileUri: fileUri ?? this.fileUri,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (artifactId.present) {
      map['artifact_id'] = Variable<String>(artifactId.value);
    }
    if (manifestId.present) {
      map['manifest_id'] = Variable<String>(manifestId.value);
    }
    if (kind.present) {
      map['kind'] = Variable<String>(kind.value);
    }
    if (fileUri.present) {
      map['file_uri'] = Variable<String>(fileUri.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('ArtifactRowsCompanion(')
          ..write('artifactId: $artifactId, ')
          ..write('manifestId: $manifestId, ')
          ..write('kind: $kind, ')
          ..write('fileUri: $fileUri, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $DeleteJobRowsTable extends DeleteJobRows
    with TableInfo<$DeleteJobRowsTable, DeleteJobRecord> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $DeleteJobRowsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _jobIdMeta = const VerificationMeta('jobId');
  @override
  late final GeneratedColumn<String> jobId = GeneratedColumn<String>(
    'job_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _artifactManifestIdMeta =
      const VerificationMeta('artifactManifestId');
  @override
  late final GeneratedColumn<String> artifactManifestId =
      GeneratedColumn<String>(
        'artifact_manifest_id',
        aliasedName,
        false,
        type: DriftSqlType.string,
        requiredDuringInsert: true,
        defaultConstraints: GeneratedColumn.constraintIsAlways(
          'REFERENCES artifact_manifests (manifest_id) ON DELETE RESTRICT',
        ),
      );
  static const VerificationMeta _statusMeta = const VerificationMeta('status');
  @override
  late final GeneratedColumn<String> status = GeneratedColumn<String>(
    'status',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _attemptsMeta = const VerificationMeta(
    'attempts',
  );
  @override
  late final GeneratedColumn<int> attempts = GeneratedColumn<int>(
    'attempts',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  static const VerificationMeta _failureCodeMeta = const VerificationMeta(
    'failureCode',
  );
  @override
  late final GeneratedColumn<String> failureCode = GeneratedColumn<String>(
    'failure_code',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  @override
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
    'updated_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [
    jobId,
    artifactManifestId,
    status,
    attempts,
    failureCode,
    createdAt,
    updatedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'delete_job_rows';
  @override
  VerificationContext validateIntegrity(
    Insertable<DeleteJobRecord> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('job_id')) {
      context.handle(
        _jobIdMeta,
        jobId.isAcceptableOrUnknown(data['job_id']!, _jobIdMeta),
      );
    } else if (isInserting) {
      context.missing(_jobIdMeta);
    }
    if (data.containsKey('artifact_manifest_id')) {
      context.handle(
        _artifactManifestIdMeta,
        artifactManifestId.isAcceptableOrUnknown(
          data['artifact_manifest_id']!,
          _artifactManifestIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_artifactManifestIdMeta);
    }
    if (data.containsKey('status')) {
      context.handle(
        _statusMeta,
        status.isAcceptableOrUnknown(data['status']!, _statusMeta),
      );
    } else if (isInserting) {
      context.missing(_statusMeta);
    }
    if (data.containsKey('attempts')) {
      context.handle(
        _attemptsMeta,
        attempts.isAcceptableOrUnknown(data['attempts']!, _attemptsMeta),
      );
    }
    if (data.containsKey('failure_code')) {
      context.handle(
        _failureCodeMeta,
        failureCode.isAcceptableOrUnknown(
          data['failure_code']!,
          _failureCodeMeta,
        ),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    } else if (isInserting) {
      context.missing(_createdAtMeta);
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_updatedAtMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {jobId};
  @override
  DeleteJobRecord map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return DeleteJobRecord(
      jobId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}job_id'],
      )!,
      artifactManifestId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}artifact_manifest_id'],
      )!,
      status: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}status'],
      )!,
      attempts: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}attempts'],
      )!,
      failureCode: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}failure_code'],
      ),
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      )!,
    );
  }

  @override
  $DeleteJobRowsTable createAlias(String alias) {
    return $DeleteJobRowsTable(attachedDatabase, alias);
  }
}

class DeleteJobRecord extends DataClass implements Insertable<DeleteJobRecord> {
  final String jobId;
  final String artifactManifestId;
  final String status;
  final int attempts;
  final String? failureCode;
  final DateTime createdAt;
  final DateTime updatedAt;
  const DeleteJobRecord({
    required this.jobId,
    required this.artifactManifestId,
    required this.status,
    required this.attempts,
    this.failureCode,
    required this.createdAt,
    required this.updatedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['job_id'] = Variable<String>(jobId);
    map['artifact_manifest_id'] = Variable<String>(artifactManifestId);
    map['status'] = Variable<String>(status);
    map['attempts'] = Variable<int>(attempts);
    if (!nullToAbsent || failureCode != null) {
      map['failure_code'] = Variable<String>(failureCode);
    }
    map['created_at'] = Variable<DateTime>(createdAt);
    map['updated_at'] = Variable<DateTime>(updatedAt);
    return map;
  }

  DeleteJobRowsCompanion toCompanion(bool nullToAbsent) {
    return DeleteJobRowsCompanion(
      jobId: Value(jobId),
      artifactManifestId: Value(artifactManifestId),
      status: Value(status),
      attempts: Value(attempts),
      failureCode: failureCode == null && nullToAbsent
          ? const Value.absent()
          : Value(failureCode),
      createdAt: Value(createdAt),
      updatedAt: Value(updatedAt),
    );
  }

  factory DeleteJobRecord.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return DeleteJobRecord(
      jobId: serializer.fromJson<String>(json['jobId']),
      artifactManifestId: serializer.fromJson<String>(
        json['artifactManifestId'],
      ),
      status: serializer.fromJson<String>(json['status']),
      attempts: serializer.fromJson<int>(json['attempts']),
      failureCode: serializer.fromJson<String?>(json['failureCode']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
      updatedAt: serializer.fromJson<DateTime>(json['updatedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'jobId': serializer.toJson<String>(jobId),
      'artifactManifestId': serializer.toJson<String>(artifactManifestId),
      'status': serializer.toJson<String>(status),
      'attempts': serializer.toJson<int>(attempts),
      'failureCode': serializer.toJson<String?>(failureCode),
      'createdAt': serializer.toJson<DateTime>(createdAt),
      'updatedAt': serializer.toJson<DateTime>(updatedAt),
    };
  }

  DeleteJobRecord copyWith({
    String? jobId,
    String? artifactManifestId,
    String? status,
    int? attempts,
    Value<String?> failureCode = const Value.absent(),
    DateTime? createdAt,
    DateTime? updatedAt,
  }) => DeleteJobRecord(
    jobId: jobId ?? this.jobId,
    artifactManifestId: artifactManifestId ?? this.artifactManifestId,
    status: status ?? this.status,
    attempts: attempts ?? this.attempts,
    failureCode: failureCode.present ? failureCode.value : this.failureCode,
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt ?? this.updatedAt,
  );
  DeleteJobRecord copyWithCompanion(DeleteJobRowsCompanion data) {
    return DeleteJobRecord(
      jobId: data.jobId.present ? data.jobId.value : this.jobId,
      artifactManifestId: data.artifactManifestId.present
          ? data.artifactManifestId.value
          : this.artifactManifestId,
      status: data.status.present ? data.status.value : this.status,
      attempts: data.attempts.present ? data.attempts.value : this.attempts,
      failureCode: data.failureCode.present
          ? data.failureCode.value
          : this.failureCode,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('DeleteJobRecord(')
          ..write('jobId: $jobId, ')
          ..write('artifactManifestId: $artifactManifestId, ')
          ..write('status: $status, ')
          ..write('attempts: $attempts, ')
          ..write('failureCode: $failureCode, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    jobId,
    artifactManifestId,
    status,
    attempts,
    failureCode,
    createdAt,
    updatedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is DeleteJobRecord &&
          other.jobId == this.jobId &&
          other.artifactManifestId == this.artifactManifestId &&
          other.status == this.status &&
          other.attempts == this.attempts &&
          other.failureCode == this.failureCode &&
          other.createdAt == this.createdAt &&
          other.updatedAt == this.updatedAt);
}

class DeleteJobRowsCompanion extends UpdateCompanion<DeleteJobRecord> {
  final Value<String> jobId;
  final Value<String> artifactManifestId;
  final Value<String> status;
  final Value<int> attempts;
  final Value<String?> failureCode;
  final Value<DateTime> createdAt;
  final Value<DateTime> updatedAt;
  final Value<int> rowid;
  const DeleteJobRowsCompanion({
    this.jobId = const Value.absent(),
    this.artifactManifestId = const Value.absent(),
    this.status = const Value.absent(),
    this.attempts = const Value.absent(),
    this.failureCode = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  DeleteJobRowsCompanion.insert({
    required String jobId,
    required String artifactManifestId,
    required String status,
    this.attempts = const Value.absent(),
    this.failureCode = const Value.absent(),
    required DateTime createdAt,
    required DateTime updatedAt,
    this.rowid = const Value.absent(),
  }) : jobId = Value(jobId),
       artifactManifestId = Value(artifactManifestId),
       status = Value(status),
       createdAt = Value(createdAt),
       updatedAt = Value(updatedAt);
  static Insertable<DeleteJobRecord> custom({
    Expression<String>? jobId,
    Expression<String>? artifactManifestId,
    Expression<String>? status,
    Expression<int>? attempts,
    Expression<String>? failureCode,
    Expression<DateTime>? createdAt,
    Expression<DateTime>? updatedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (jobId != null) 'job_id': jobId,
      if (artifactManifestId != null)
        'artifact_manifest_id': artifactManifestId,
      if (status != null) 'status': status,
      if (attempts != null) 'attempts': attempts,
      if (failureCode != null) 'failure_code': failureCode,
      if (createdAt != null) 'created_at': createdAt,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  DeleteJobRowsCompanion copyWith({
    Value<String>? jobId,
    Value<String>? artifactManifestId,
    Value<String>? status,
    Value<int>? attempts,
    Value<String?>? failureCode,
    Value<DateTime>? createdAt,
    Value<DateTime>? updatedAt,
    Value<int>? rowid,
  }) {
    return DeleteJobRowsCompanion(
      jobId: jobId ?? this.jobId,
      artifactManifestId: artifactManifestId ?? this.artifactManifestId,
      status: status ?? this.status,
      attempts: attempts ?? this.attempts,
      failureCode: failureCode ?? this.failureCode,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (jobId.present) {
      map['job_id'] = Variable<String>(jobId.value);
    }
    if (artifactManifestId.present) {
      map['artifact_manifest_id'] = Variable<String>(artifactManifestId.value);
    }
    if (status.present) {
      map['status'] = Variable<String>(status.value);
    }
    if (attempts.present) {
      map['attempts'] = Variable<int>(attempts.value);
    }
    if (failureCode.present) {
      map['failure_code'] = Variable<String>(failureCode.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('DeleteJobRowsCompanion(')
          ..write('jobId: $jobId, ')
          ..write('artifactManifestId: $artifactManifestId, ')
          ..write('status: $status, ')
          ..write('attempts: $attempts, ')
          ..write('failureCode: $failureCode, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $BangumiAccountsTable extends BangumiAccounts
    with TableInfo<$BangumiAccountsTable, BangumiAccountRecord> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $BangumiAccountsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _accountIdMeta = const VerificationMeta(
    'accountId',
  );
  @override
  late final GeneratedColumn<String> accountId = GeneratedColumn<String>(
    'account_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _usernameMeta = const VerificationMeta(
    'username',
  );
  @override
  late final GeneratedColumn<String> username = GeneratedColumn<String>(
    'username',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _nicknameMeta = const VerificationMeta(
    'nickname',
  );
  @override
  late final GeneratedColumn<String> nickname = GeneratedColumn<String>(
    'nickname',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _avatarUrlMeta = const VerificationMeta(
    'avatarUrl',
  );
  @override
  late final GeneratedColumn<String> avatarUrl = GeneratedColumn<String>(
    'avatar_url',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _isActiveMeta = const VerificationMeta(
    'isActive',
  );
  @override
  late final GeneratedColumn<bool> isActive = GeneratedColumn<bool>(
    'is_active',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("is_active" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _scheduleUpdatedAtMeta = const VerificationMeta(
    'scheduleUpdatedAt',
  );
  @override
  late final GeneratedColumn<DateTime> scheduleUpdatedAt =
      GeneratedColumn<DateTime>(
        'schedule_updated_at',
        aliasedName,
        true,
        type: DriftSqlType.dateTime,
        requiredDuringInsert: false,
      );
  static const VerificationMeta _lastSeenAtMeta = const VerificationMeta(
    'lastSeenAt',
  );
  @override
  late final GeneratedColumn<DateTime> lastSeenAt = GeneratedColumn<DateTime>(
    'last_seen_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [
    accountId,
    username,
    nickname,
    avatarUrl,
    isActive,
    scheduleUpdatedAt,
    lastSeenAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'bangumi_accounts';
  @override
  VerificationContext validateIntegrity(
    Insertable<BangumiAccountRecord> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('account_id')) {
      context.handle(
        _accountIdMeta,
        accountId.isAcceptableOrUnknown(data['account_id']!, _accountIdMeta),
      );
    } else if (isInserting) {
      context.missing(_accountIdMeta);
    }
    if (data.containsKey('username')) {
      context.handle(
        _usernameMeta,
        username.isAcceptableOrUnknown(data['username']!, _usernameMeta),
      );
    } else if (isInserting) {
      context.missing(_usernameMeta);
    }
    if (data.containsKey('nickname')) {
      context.handle(
        _nicknameMeta,
        nickname.isAcceptableOrUnknown(data['nickname']!, _nicknameMeta),
      );
    }
    if (data.containsKey('avatar_url')) {
      context.handle(
        _avatarUrlMeta,
        avatarUrl.isAcceptableOrUnknown(data['avatar_url']!, _avatarUrlMeta),
      );
    }
    if (data.containsKey('is_active')) {
      context.handle(
        _isActiveMeta,
        isActive.isAcceptableOrUnknown(data['is_active']!, _isActiveMeta),
      );
    }
    if (data.containsKey('schedule_updated_at')) {
      context.handle(
        _scheduleUpdatedAtMeta,
        scheduleUpdatedAt.isAcceptableOrUnknown(
          data['schedule_updated_at']!,
          _scheduleUpdatedAtMeta,
        ),
      );
    }
    if (data.containsKey('last_seen_at')) {
      context.handle(
        _lastSeenAtMeta,
        lastSeenAt.isAcceptableOrUnknown(
          data['last_seen_at']!,
          _lastSeenAtMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_lastSeenAtMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {accountId};
  @override
  BangumiAccountRecord map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return BangumiAccountRecord(
      accountId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}account_id'],
      )!,
      username: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}username'],
      )!,
      nickname: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}nickname'],
      ),
      avatarUrl: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}avatar_url'],
      ),
      isActive: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}is_active'],
      )!,
      scheduleUpdatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}schedule_updated_at'],
      ),
      lastSeenAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}last_seen_at'],
      )!,
    );
  }

  @override
  $BangumiAccountsTable createAlias(String alias) {
    return $BangumiAccountsTable(attachedDatabase, alias);
  }
}

class BangumiAccountRecord extends DataClass
    implements Insertable<BangumiAccountRecord> {
  final String accountId;
  final String username;
  final String? nickname;
  final String? avatarUrl;
  final bool isActive;
  final DateTime? scheduleUpdatedAt;
  final DateTime lastSeenAt;
  const BangumiAccountRecord({
    required this.accountId,
    required this.username,
    this.nickname,
    this.avatarUrl,
    required this.isActive,
    this.scheduleUpdatedAt,
    required this.lastSeenAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['account_id'] = Variable<String>(accountId);
    map['username'] = Variable<String>(username);
    if (!nullToAbsent || nickname != null) {
      map['nickname'] = Variable<String>(nickname);
    }
    if (!nullToAbsent || avatarUrl != null) {
      map['avatar_url'] = Variable<String>(avatarUrl);
    }
    map['is_active'] = Variable<bool>(isActive);
    if (!nullToAbsent || scheduleUpdatedAt != null) {
      map['schedule_updated_at'] = Variable<DateTime>(scheduleUpdatedAt);
    }
    map['last_seen_at'] = Variable<DateTime>(lastSeenAt);
    return map;
  }

  BangumiAccountsCompanion toCompanion(bool nullToAbsent) {
    return BangumiAccountsCompanion(
      accountId: Value(accountId),
      username: Value(username),
      nickname: nickname == null && nullToAbsent
          ? const Value.absent()
          : Value(nickname),
      avatarUrl: avatarUrl == null && nullToAbsent
          ? const Value.absent()
          : Value(avatarUrl),
      isActive: Value(isActive),
      scheduleUpdatedAt: scheduleUpdatedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(scheduleUpdatedAt),
      lastSeenAt: Value(lastSeenAt),
    );
  }

  factory BangumiAccountRecord.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return BangumiAccountRecord(
      accountId: serializer.fromJson<String>(json['accountId']),
      username: serializer.fromJson<String>(json['username']),
      nickname: serializer.fromJson<String?>(json['nickname']),
      avatarUrl: serializer.fromJson<String?>(json['avatarUrl']),
      isActive: serializer.fromJson<bool>(json['isActive']),
      scheduleUpdatedAt: serializer.fromJson<DateTime?>(
        json['scheduleUpdatedAt'],
      ),
      lastSeenAt: serializer.fromJson<DateTime>(json['lastSeenAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'accountId': serializer.toJson<String>(accountId),
      'username': serializer.toJson<String>(username),
      'nickname': serializer.toJson<String?>(nickname),
      'avatarUrl': serializer.toJson<String?>(avatarUrl),
      'isActive': serializer.toJson<bool>(isActive),
      'scheduleUpdatedAt': serializer.toJson<DateTime?>(scheduleUpdatedAt),
      'lastSeenAt': serializer.toJson<DateTime>(lastSeenAt),
    };
  }

  BangumiAccountRecord copyWith({
    String? accountId,
    String? username,
    Value<String?> nickname = const Value.absent(),
    Value<String?> avatarUrl = const Value.absent(),
    bool? isActive,
    Value<DateTime?> scheduleUpdatedAt = const Value.absent(),
    DateTime? lastSeenAt,
  }) => BangumiAccountRecord(
    accountId: accountId ?? this.accountId,
    username: username ?? this.username,
    nickname: nickname.present ? nickname.value : this.nickname,
    avatarUrl: avatarUrl.present ? avatarUrl.value : this.avatarUrl,
    isActive: isActive ?? this.isActive,
    scheduleUpdatedAt: scheduleUpdatedAt.present
        ? scheduleUpdatedAt.value
        : this.scheduleUpdatedAt,
    lastSeenAt: lastSeenAt ?? this.lastSeenAt,
  );
  BangumiAccountRecord copyWithCompanion(BangumiAccountsCompanion data) {
    return BangumiAccountRecord(
      accountId: data.accountId.present ? data.accountId.value : this.accountId,
      username: data.username.present ? data.username.value : this.username,
      nickname: data.nickname.present ? data.nickname.value : this.nickname,
      avatarUrl: data.avatarUrl.present ? data.avatarUrl.value : this.avatarUrl,
      isActive: data.isActive.present ? data.isActive.value : this.isActive,
      scheduleUpdatedAt: data.scheduleUpdatedAt.present
          ? data.scheduleUpdatedAt.value
          : this.scheduleUpdatedAt,
      lastSeenAt: data.lastSeenAt.present
          ? data.lastSeenAt.value
          : this.lastSeenAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('BangumiAccountRecord(')
          ..write('accountId: $accountId, ')
          ..write('username: $username, ')
          ..write('nickname: $nickname, ')
          ..write('avatarUrl: $avatarUrl, ')
          ..write('isActive: $isActive, ')
          ..write('scheduleUpdatedAt: $scheduleUpdatedAt, ')
          ..write('lastSeenAt: $lastSeenAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    accountId,
    username,
    nickname,
    avatarUrl,
    isActive,
    scheduleUpdatedAt,
    lastSeenAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is BangumiAccountRecord &&
          other.accountId == this.accountId &&
          other.username == this.username &&
          other.nickname == this.nickname &&
          other.avatarUrl == this.avatarUrl &&
          other.isActive == this.isActive &&
          other.scheduleUpdatedAt == this.scheduleUpdatedAt &&
          other.lastSeenAt == this.lastSeenAt);
}

class BangumiAccountsCompanion extends UpdateCompanion<BangumiAccountRecord> {
  final Value<String> accountId;
  final Value<String> username;
  final Value<String?> nickname;
  final Value<String?> avatarUrl;
  final Value<bool> isActive;
  final Value<DateTime?> scheduleUpdatedAt;
  final Value<DateTime> lastSeenAt;
  final Value<int> rowid;
  const BangumiAccountsCompanion({
    this.accountId = const Value.absent(),
    this.username = const Value.absent(),
    this.nickname = const Value.absent(),
    this.avatarUrl = const Value.absent(),
    this.isActive = const Value.absent(),
    this.scheduleUpdatedAt = const Value.absent(),
    this.lastSeenAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  BangumiAccountsCompanion.insert({
    required String accountId,
    required String username,
    this.nickname = const Value.absent(),
    this.avatarUrl = const Value.absent(),
    this.isActive = const Value.absent(),
    this.scheduleUpdatedAt = const Value.absent(),
    required DateTime lastSeenAt,
    this.rowid = const Value.absent(),
  }) : accountId = Value(accountId),
       username = Value(username),
       lastSeenAt = Value(lastSeenAt);
  static Insertable<BangumiAccountRecord> custom({
    Expression<String>? accountId,
    Expression<String>? username,
    Expression<String>? nickname,
    Expression<String>? avatarUrl,
    Expression<bool>? isActive,
    Expression<DateTime>? scheduleUpdatedAt,
    Expression<DateTime>? lastSeenAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (accountId != null) 'account_id': accountId,
      if (username != null) 'username': username,
      if (nickname != null) 'nickname': nickname,
      if (avatarUrl != null) 'avatar_url': avatarUrl,
      if (isActive != null) 'is_active': isActive,
      if (scheduleUpdatedAt != null) 'schedule_updated_at': scheduleUpdatedAt,
      if (lastSeenAt != null) 'last_seen_at': lastSeenAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  BangumiAccountsCompanion copyWith({
    Value<String>? accountId,
    Value<String>? username,
    Value<String?>? nickname,
    Value<String?>? avatarUrl,
    Value<bool>? isActive,
    Value<DateTime?>? scheduleUpdatedAt,
    Value<DateTime>? lastSeenAt,
    Value<int>? rowid,
  }) {
    return BangumiAccountsCompanion(
      accountId: accountId ?? this.accountId,
      username: username ?? this.username,
      nickname: nickname ?? this.nickname,
      avatarUrl: avatarUrl ?? this.avatarUrl,
      isActive: isActive ?? this.isActive,
      scheduleUpdatedAt: scheduleUpdatedAt ?? this.scheduleUpdatedAt,
      lastSeenAt: lastSeenAt ?? this.lastSeenAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (accountId.present) {
      map['account_id'] = Variable<String>(accountId.value);
    }
    if (username.present) {
      map['username'] = Variable<String>(username.value);
    }
    if (nickname.present) {
      map['nickname'] = Variable<String>(nickname.value);
    }
    if (avatarUrl.present) {
      map['avatar_url'] = Variable<String>(avatarUrl.value);
    }
    if (isActive.present) {
      map['is_active'] = Variable<bool>(isActive.value);
    }
    if (scheduleUpdatedAt.present) {
      map['schedule_updated_at'] = Variable<DateTime>(scheduleUpdatedAt.value);
    }
    if (lastSeenAt.present) {
      map['last_seen_at'] = Variable<DateTime>(lastSeenAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('BangumiAccountsCompanion(')
          ..write('accountId: $accountId, ')
          ..write('username: $username, ')
          ..write('nickname: $nickname, ')
          ..write('avatarUrl: $avatarUrl, ')
          ..write('isActive: $isActive, ')
          ..write('scheduleUpdatedAt: $scheduleUpdatedAt, ')
          ..write('lastSeenAt: $lastSeenAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $BangumiSchedulesTable extends BangumiSchedules
    with TableInfo<$BangumiSchedulesTable, BangumiScheduleRecord> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $BangumiSchedulesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _accountIdMeta = const VerificationMeta(
    'accountId',
  );
  @override
  late final GeneratedColumn<String> accountId = GeneratedColumn<String>(
    'account_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES bangumi_accounts (account_id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _entryIdMeta = const VerificationMeta(
    'entryId',
  );
  @override
  late final GeneratedColumn<String> entryId = GeneratedColumn<String>(
    'entry_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _subjectIdMeta = const VerificationMeta(
    'subjectId',
  );
  @override
  late final GeneratedColumn<String> subjectId = GeneratedColumn<String>(
    'subject_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _subjectNameMeta = const VerificationMeta(
    'subjectName',
  );
  @override
  late final GeneratedColumn<String> subjectName = GeneratedColumn<String>(
    'subject_name',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _airWeekdayMeta = const VerificationMeta(
    'airWeekday',
  );
  @override
  late final GeneratedColumn<int> airWeekday = GeneratedColumn<int>(
    'air_weekday',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _airDateMeta = const VerificationMeta(
    'airDate',
  );
  @override
  late final GeneratedColumn<DateTime> airDate = GeneratedColumn<DateTime>(
    'air_date',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _episodeNumberMeta = const VerificationMeta(
    'episodeNumber',
  );
  @override
  late final GeneratedColumn<double> episodeNumber = GeneratedColumn<double>(
    'episode_number',
    aliasedName,
    true,
    type: DriftSqlType.double,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _imageUrlMeta = const VerificationMeta(
    'imageUrl',
  );
  @override
  late final GeneratedColumn<String> imageUrl = GeneratedColumn<String>(
    'image_url',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  @override
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
    'updated_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [
    accountId,
    entryId,
    subjectId,
    subjectName,
    airWeekday,
    airDate,
    episodeNumber,
    imageUrl,
    updatedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'bangumi_schedules';
  @override
  VerificationContext validateIntegrity(
    Insertable<BangumiScheduleRecord> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('account_id')) {
      context.handle(
        _accountIdMeta,
        accountId.isAcceptableOrUnknown(data['account_id']!, _accountIdMeta),
      );
    } else if (isInserting) {
      context.missing(_accountIdMeta);
    }
    if (data.containsKey('entry_id')) {
      context.handle(
        _entryIdMeta,
        entryId.isAcceptableOrUnknown(data['entry_id']!, _entryIdMeta),
      );
    } else if (isInserting) {
      context.missing(_entryIdMeta);
    }
    if (data.containsKey('subject_id')) {
      context.handle(
        _subjectIdMeta,
        subjectId.isAcceptableOrUnknown(data['subject_id']!, _subjectIdMeta),
      );
    } else if (isInserting) {
      context.missing(_subjectIdMeta);
    }
    if (data.containsKey('subject_name')) {
      context.handle(
        _subjectNameMeta,
        subjectName.isAcceptableOrUnknown(
          data['subject_name']!,
          _subjectNameMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_subjectNameMeta);
    }
    if (data.containsKey('air_weekday')) {
      context.handle(
        _airWeekdayMeta,
        airWeekday.isAcceptableOrUnknown(data['air_weekday']!, _airWeekdayMeta),
      );
    } else if (isInserting) {
      context.missing(_airWeekdayMeta);
    }
    if (data.containsKey('air_date')) {
      context.handle(
        _airDateMeta,
        airDate.isAcceptableOrUnknown(data['air_date']!, _airDateMeta),
      );
    }
    if (data.containsKey('episode_number')) {
      context.handle(
        _episodeNumberMeta,
        episodeNumber.isAcceptableOrUnknown(
          data['episode_number']!,
          _episodeNumberMeta,
        ),
      );
    }
    if (data.containsKey('image_url')) {
      context.handle(
        _imageUrlMeta,
        imageUrl.isAcceptableOrUnknown(data['image_url']!, _imageUrlMeta),
      );
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_updatedAtMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {accountId, entryId};
  @override
  BangumiScheduleRecord map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return BangumiScheduleRecord(
      accountId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}account_id'],
      )!,
      entryId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}entry_id'],
      )!,
      subjectId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}subject_id'],
      )!,
      subjectName: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}subject_name'],
      )!,
      airWeekday: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}air_weekday'],
      )!,
      airDate: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}air_date'],
      ),
      episodeNumber: attachedDatabase.typeMapping.read(
        DriftSqlType.double,
        data['${effectivePrefix}episode_number'],
      ),
      imageUrl: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}image_url'],
      ),
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      )!,
    );
  }

  @override
  $BangumiSchedulesTable createAlias(String alias) {
    return $BangumiSchedulesTable(attachedDatabase, alias);
  }
}

class BangumiScheduleRecord extends DataClass
    implements Insertable<BangumiScheduleRecord> {
  final String accountId;
  final String entryId;
  final String subjectId;
  final String subjectName;
  final int airWeekday;
  final DateTime? airDate;
  final double? episodeNumber;
  final String? imageUrl;
  final DateTime updatedAt;
  const BangumiScheduleRecord({
    required this.accountId,
    required this.entryId,
    required this.subjectId,
    required this.subjectName,
    required this.airWeekday,
    this.airDate,
    this.episodeNumber,
    this.imageUrl,
    required this.updatedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['account_id'] = Variable<String>(accountId);
    map['entry_id'] = Variable<String>(entryId);
    map['subject_id'] = Variable<String>(subjectId);
    map['subject_name'] = Variable<String>(subjectName);
    map['air_weekday'] = Variable<int>(airWeekday);
    if (!nullToAbsent || airDate != null) {
      map['air_date'] = Variable<DateTime>(airDate);
    }
    if (!nullToAbsent || episodeNumber != null) {
      map['episode_number'] = Variable<double>(episodeNumber);
    }
    if (!nullToAbsent || imageUrl != null) {
      map['image_url'] = Variable<String>(imageUrl);
    }
    map['updated_at'] = Variable<DateTime>(updatedAt);
    return map;
  }

  BangumiSchedulesCompanion toCompanion(bool nullToAbsent) {
    return BangumiSchedulesCompanion(
      accountId: Value(accountId),
      entryId: Value(entryId),
      subjectId: Value(subjectId),
      subjectName: Value(subjectName),
      airWeekday: Value(airWeekday),
      airDate: airDate == null && nullToAbsent
          ? const Value.absent()
          : Value(airDate),
      episodeNumber: episodeNumber == null && nullToAbsent
          ? const Value.absent()
          : Value(episodeNumber),
      imageUrl: imageUrl == null && nullToAbsent
          ? const Value.absent()
          : Value(imageUrl),
      updatedAt: Value(updatedAt),
    );
  }

  factory BangumiScheduleRecord.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return BangumiScheduleRecord(
      accountId: serializer.fromJson<String>(json['accountId']),
      entryId: serializer.fromJson<String>(json['entryId']),
      subjectId: serializer.fromJson<String>(json['subjectId']),
      subjectName: serializer.fromJson<String>(json['subjectName']),
      airWeekday: serializer.fromJson<int>(json['airWeekday']),
      airDate: serializer.fromJson<DateTime?>(json['airDate']),
      episodeNumber: serializer.fromJson<double?>(json['episodeNumber']),
      imageUrl: serializer.fromJson<String?>(json['imageUrl']),
      updatedAt: serializer.fromJson<DateTime>(json['updatedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'accountId': serializer.toJson<String>(accountId),
      'entryId': serializer.toJson<String>(entryId),
      'subjectId': serializer.toJson<String>(subjectId),
      'subjectName': serializer.toJson<String>(subjectName),
      'airWeekday': serializer.toJson<int>(airWeekday),
      'airDate': serializer.toJson<DateTime?>(airDate),
      'episodeNumber': serializer.toJson<double?>(episodeNumber),
      'imageUrl': serializer.toJson<String?>(imageUrl),
      'updatedAt': serializer.toJson<DateTime>(updatedAt),
    };
  }

  BangumiScheduleRecord copyWith({
    String? accountId,
    String? entryId,
    String? subjectId,
    String? subjectName,
    int? airWeekday,
    Value<DateTime?> airDate = const Value.absent(),
    Value<double?> episodeNumber = const Value.absent(),
    Value<String?> imageUrl = const Value.absent(),
    DateTime? updatedAt,
  }) => BangumiScheduleRecord(
    accountId: accountId ?? this.accountId,
    entryId: entryId ?? this.entryId,
    subjectId: subjectId ?? this.subjectId,
    subjectName: subjectName ?? this.subjectName,
    airWeekday: airWeekday ?? this.airWeekday,
    airDate: airDate.present ? airDate.value : this.airDate,
    episodeNumber: episodeNumber.present
        ? episodeNumber.value
        : this.episodeNumber,
    imageUrl: imageUrl.present ? imageUrl.value : this.imageUrl,
    updatedAt: updatedAt ?? this.updatedAt,
  );
  BangumiScheduleRecord copyWithCompanion(BangumiSchedulesCompanion data) {
    return BangumiScheduleRecord(
      accountId: data.accountId.present ? data.accountId.value : this.accountId,
      entryId: data.entryId.present ? data.entryId.value : this.entryId,
      subjectId: data.subjectId.present ? data.subjectId.value : this.subjectId,
      subjectName: data.subjectName.present
          ? data.subjectName.value
          : this.subjectName,
      airWeekday: data.airWeekday.present
          ? data.airWeekday.value
          : this.airWeekday,
      airDate: data.airDate.present ? data.airDate.value : this.airDate,
      episodeNumber: data.episodeNumber.present
          ? data.episodeNumber.value
          : this.episodeNumber,
      imageUrl: data.imageUrl.present ? data.imageUrl.value : this.imageUrl,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('BangumiScheduleRecord(')
          ..write('accountId: $accountId, ')
          ..write('entryId: $entryId, ')
          ..write('subjectId: $subjectId, ')
          ..write('subjectName: $subjectName, ')
          ..write('airWeekday: $airWeekday, ')
          ..write('airDate: $airDate, ')
          ..write('episodeNumber: $episodeNumber, ')
          ..write('imageUrl: $imageUrl, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    accountId,
    entryId,
    subjectId,
    subjectName,
    airWeekday,
    airDate,
    episodeNumber,
    imageUrl,
    updatedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is BangumiScheduleRecord &&
          other.accountId == this.accountId &&
          other.entryId == this.entryId &&
          other.subjectId == this.subjectId &&
          other.subjectName == this.subjectName &&
          other.airWeekday == this.airWeekday &&
          other.airDate == this.airDate &&
          other.episodeNumber == this.episodeNumber &&
          other.imageUrl == this.imageUrl &&
          other.updatedAt == this.updatedAt);
}

class BangumiSchedulesCompanion extends UpdateCompanion<BangumiScheduleRecord> {
  final Value<String> accountId;
  final Value<String> entryId;
  final Value<String> subjectId;
  final Value<String> subjectName;
  final Value<int> airWeekday;
  final Value<DateTime?> airDate;
  final Value<double?> episodeNumber;
  final Value<String?> imageUrl;
  final Value<DateTime> updatedAt;
  final Value<int> rowid;
  const BangumiSchedulesCompanion({
    this.accountId = const Value.absent(),
    this.entryId = const Value.absent(),
    this.subjectId = const Value.absent(),
    this.subjectName = const Value.absent(),
    this.airWeekday = const Value.absent(),
    this.airDate = const Value.absent(),
    this.episodeNumber = const Value.absent(),
    this.imageUrl = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  BangumiSchedulesCompanion.insert({
    required String accountId,
    required String entryId,
    required String subjectId,
    required String subjectName,
    required int airWeekday,
    this.airDate = const Value.absent(),
    this.episodeNumber = const Value.absent(),
    this.imageUrl = const Value.absent(),
    required DateTime updatedAt,
    this.rowid = const Value.absent(),
  }) : accountId = Value(accountId),
       entryId = Value(entryId),
       subjectId = Value(subjectId),
       subjectName = Value(subjectName),
       airWeekday = Value(airWeekday),
       updatedAt = Value(updatedAt);
  static Insertable<BangumiScheduleRecord> custom({
    Expression<String>? accountId,
    Expression<String>? entryId,
    Expression<String>? subjectId,
    Expression<String>? subjectName,
    Expression<int>? airWeekday,
    Expression<DateTime>? airDate,
    Expression<double>? episodeNumber,
    Expression<String>? imageUrl,
    Expression<DateTime>? updatedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (accountId != null) 'account_id': accountId,
      if (entryId != null) 'entry_id': entryId,
      if (subjectId != null) 'subject_id': subjectId,
      if (subjectName != null) 'subject_name': subjectName,
      if (airWeekday != null) 'air_weekday': airWeekday,
      if (airDate != null) 'air_date': airDate,
      if (episodeNumber != null) 'episode_number': episodeNumber,
      if (imageUrl != null) 'image_url': imageUrl,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  BangumiSchedulesCompanion copyWith({
    Value<String>? accountId,
    Value<String>? entryId,
    Value<String>? subjectId,
    Value<String>? subjectName,
    Value<int>? airWeekday,
    Value<DateTime?>? airDate,
    Value<double?>? episodeNumber,
    Value<String?>? imageUrl,
    Value<DateTime>? updatedAt,
    Value<int>? rowid,
  }) {
    return BangumiSchedulesCompanion(
      accountId: accountId ?? this.accountId,
      entryId: entryId ?? this.entryId,
      subjectId: subjectId ?? this.subjectId,
      subjectName: subjectName ?? this.subjectName,
      airWeekday: airWeekday ?? this.airWeekday,
      airDate: airDate ?? this.airDate,
      episodeNumber: episodeNumber ?? this.episodeNumber,
      imageUrl: imageUrl ?? this.imageUrl,
      updatedAt: updatedAt ?? this.updatedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (accountId.present) {
      map['account_id'] = Variable<String>(accountId.value);
    }
    if (entryId.present) {
      map['entry_id'] = Variable<String>(entryId.value);
    }
    if (subjectId.present) {
      map['subject_id'] = Variable<String>(subjectId.value);
    }
    if (subjectName.present) {
      map['subject_name'] = Variable<String>(subjectName.value);
    }
    if (airWeekday.present) {
      map['air_weekday'] = Variable<int>(airWeekday.value);
    }
    if (airDate.present) {
      map['air_date'] = Variable<DateTime>(airDate.value);
    }
    if (episodeNumber.present) {
      map['episode_number'] = Variable<double>(episodeNumber.value);
    }
    if (imageUrl.present) {
      map['image_url'] = Variable<String>(imageUrl.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('BangumiSchedulesCompanion(')
          ..write('accountId: $accountId, ')
          ..write('entryId: $entryId, ')
          ..write('subjectId: $subjectId, ')
          ..write('subjectName: $subjectName, ')
          ..write('airWeekday: $airWeekday, ')
          ..write('airDate: $airDate, ')
          ..write('episodeNumber: $episodeNumber, ')
          ..write('imageUrl: $imageUrl, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $BangumiSubjectsTable extends BangumiSubjects
    with TableInfo<$BangumiSubjectsTable, BangumiSubjectRecord> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $BangumiSubjectsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _subjectIdMeta = const VerificationMeta(
    'subjectId',
  );
  @override
  late final GeneratedColumn<String> subjectId = GeneratedColumn<String>(
    'subject_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _nameMeta = const VerificationMeta('name');
  @override
  late final GeneratedColumn<String> name = GeneratedColumn<String>(
    'name',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _nameCnMeta = const VerificationMeta('nameCn');
  @override
  late final GeneratedColumn<String> nameCn = GeneratedColumn<String>(
    'name_cn',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _summaryMeta = const VerificationMeta(
    'summary',
  );
  @override
  late final GeneratedColumn<String> summary = GeneratedColumn<String>(
    'summary',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _imageUrlMeta = const VerificationMeta(
    'imageUrl',
  );
  @override
  late final GeneratedColumn<String> imageUrl = GeneratedColumn<String>(
    'image_url',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _epsMeta = const VerificationMeta('eps');
  @override
  late final GeneratedColumn<int> eps = GeneratedColumn<int>(
    'eps',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  @override
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
    'updated_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [
    subjectId,
    name,
    nameCn,
    summary,
    imageUrl,
    eps,
    updatedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'bangumi_subjects';
  @override
  VerificationContext validateIntegrity(
    Insertable<BangumiSubjectRecord> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('subject_id')) {
      context.handle(
        _subjectIdMeta,
        subjectId.isAcceptableOrUnknown(data['subject_id']!, _subjectIdMeta),
      );
    } else if (isInserting) {
      context.missing(_subjectIdMeta);
    }
    if (data.containsKey('name')) {
      context.handle(
        _nameMeta,
        name.isAcceptableOrUnknown(data['name']!, _nameMeta),
      );
    } else if (isInserting) {
      context.missing(_nameMeta);
    }
    if (data.containsKey('name_cn')) {
      context.handle(
        _nameCnMeta,
        nameCn.isAcceptableOrUnknown(data['name_cn']!, _nameCnMeta),
      );
    } else if (isInserting) {
      context.missing(_nameCnMeta);
    }
    if (data.containsKey('summary')) {
      context.handle(
        _summaryMeta,
        summary.isAcceptableOrUnknown(data['summary']!, _summaryMeta),
      );
    } else if (isInserting) {
      context.missing(_summaryMeta);
    }
    if (data.containsKey('image_url')) {
      context.handle(
        _imageUrlMeta,
        imageUrl.isAcceptableOrUnknown(data['image_url']!, _imageUrlMeta),
      );
    }
    if (data.containsKey('eps')) {
      context.handle(
        _epsMeta,
        eps.isAcceptableOrUnknown(data['eps']!, _epsMeta),
      );
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_updatedAtMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {subjectId};
  @override
  BangumiSubjectRecord map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return BangumiSubjectRecord(
      subjectId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}subject_id'],
      )!,
      name: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}name'],
      )!,
      nameCn: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}name_cn'],
      )!,
      summary: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}summary'],
      )!,
      imageUrl: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}image_url'],
      ),
      eps: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}eps'],
      ),
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      )!,
    );
  }

  @override
  $BangumiSubjectsTable createAlias(String alias) {
    return $BangumiSubjectsTable(attachedDatabase, alias);
  }
}

class BangumiSubjectRecord extends DataClass
    implements Insertable<BangumiSubjectRecord> {
  final String subjectId;
  final String name;
  final String nameCn;
  final String summary;
  final String? imageUrl;
  final int? eps;
  final DateTime updatedAt;
  const BangumiSubjectRecord({
    required this.subjectId,
    required this.name,
    required this.nameCn,
    required this.summary,
    this.imageUrl,
    this.eps,
    required this.updatedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['subject_id'] = Variable<String>(subjectId);
    map['name'] = Variable<String>(name);
    map['name_cn'] = Variable<String>(nameCn);
    map['summary'] = Variable<String>(summary);
    if (!nullToAbsent || imageUrl != null) {
      map['image_url'] = Variable<String>(imageUrl);
    }
    if (!nullToAbsent || eps != null) {
      map['eps'] = Variable<int>(eps);
    }
    map['updated_at'] = Variable<DateTime>(updatedAt);
    return map;
  }

  BangumiSubjectsCompanion toCompanion(bool nullToAbsent) {
    return BangumiSubjectsCompanion(
      subjectId: Value(subjectId),
      name: Value(name),
      nameCn: Value(nameCn),
      summary: Value(summary),
      imageUrl: imageUrl == null && nullToAbsent
          ? const Value.absent()
          : Value(imageUrl),
      eps: eps == null && nullToAbsent ? const Value.absent() : Value(eps),
      updatedAt: Value(updatedAt),
    );
  }

  factory BangumiSubjectRecord.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return BangumiSubjectRecord(
      subjectId: serializer.fromJson<String>(json['subjectId']),
      name: serializer.fromJson<String>(json['name']),
      nameCn: serializer.fromJson<String>(json['nameCn']),
      summary: serializer.fromJson<String>(json['summary']),
      imageUrl: serializer.fromJson<String?>(json['imageUrl']),
      eps: serializer.fromJson<int?>(json['eps']),
      updatedAt: serializer.fromJson<DateTime>(json['updatedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'subjectId': serializer.toJson<String>(subjectId),
      'name': serializer.toJson<String>(name),
      'nameCn': serializer.toJson<String>(nameCn),
      'summary': serializer.toJson<String>(summary),
      'imageUrl': serializer.toJson<String?>(imageUrl),
      'eps': serializer.toJson<int?>(eps),
      'updatedAt': serializer.toJson<DateTime>(updatedAt),
    };
  }

  BangumiSubjectRecord copyWith({
    String? subjectId,
    String? name,
    String? nameCn,
    String? summary,
    Value<String?> imageUrl = const Value.absent(),
    Value<int?> eps = const Value.absent(),
    DateTime? updatedAt,
  }) => BangumiSubjectRecord(
    subjectId: subjectId ?? this.subjectId,
    name: name ?? this.name,
    nameCn: nameCn ?? this.nameCn,
    summary: summary ?? this.summary,
    imageUrl: imageUrl.present ? imageUrl.value : this.imageUrl,
    eps: eps.present ? eps.value : this.eps,
    updatedAt: updatedAt ?? this.updatedAt,
  );
  BangumiSubjectRecord copyWithCompanion(BangumiSubjectsCompanion data) {
    return BangumiSubjectRecord(
      subjectId: data.subjectId.present ? data.subjectId.value : this.subjectId,
      name: data.name.present ? data.name.value : this.name,
      nameCn: data.nameCn.present ? data.nameCn.value : this.nameCn,
      summary: data.summary.present ? data.summary.value : this.summary,
      imageUrl: data.imageUrl.present ? data.imageUrl.value : this.imageUrl,
      eps: data.eps.present ? data.eps.value : this.eps,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('BangumiSubjectRecord(')
          ..write('subjectId: $subjectId, ')
          ..write('name: $name, ')
          ..write('nameCn: $nameCn, ')
          ..write('summary: $summary, ')
          ..write('imageUrl: $imageUrl, ')
          ..write('eps: $eps, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode =>
      Object.hash(subjectId, name, nameCn, summary, imageUrl, eps, updatedAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is BangumiSubjectRecord &&
          other.subjectId == this.subjectId &&
          other.name == this.name &&
          other.nameCn == this.nameCn &&
          other.summary == this.summary &&
          other.imageUrl == this.imageUrl &&
          other.eps == this.eps &&
          other.updatedAt == this.updatedAt);
}

class BangumiSubjectsCompanion extends UpdateCompanion<BangumiSubjectRecord> {
  final Value<String> subjectId;
  final Value<String> name;
  final Value<String> nameCn;
  final Value<String> summary;
  final Value<String?> imageUrl;
  final Value<int?> eps;
  final Value<DateTime> updatedAt;
  final Value<int> rowid;
  const BangumiSubjectsCompanion({
    this.subjectId = const Value.absent(),
    this.name = const Value.absent(),
    this.nameCn = const Value.absent(),
    this.summary = const Value.absent(),
    this.imageUrl = const Value.absent(),
    this.eps = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  BangumiSubjectsCompanion.insert({
    required String subjectId,
    required String name,
    required String nameCn,
    required String summary,
    this.imageUrl = const Value.absent(),
    this.eps = const Value.absent(),
    required DateTime updatedAt,
    this.rowid = const Value.absent(),
  }) : subjectId = Value(subjectId),
       name = Value(name),
       nameCn = Value(nameCn),
       summary = Value(summary),
       updatedAt = Value(updatedAt);
  static Insertable<BangumiSubjectRecord> custom({
    Expression<String>? subjectId,
    Expression<String>? name,
    Expression<String>? nameCn,
    Expression<String>? summary,
    Expression<String>? imageUrl,
    Expression<int>? eps,
    Expression<DateTime>? updatedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (subjectId != null) 'subject_id': subjectId,
      if (name != null) 'name': name,
      if (nameCn != null) 'name_cn': nameCn,
      if (summary != null) 'summary': summary,
      if (imageUrl != null) 'image_url': imageUrl,
      if (eps != null) 'eps': eps,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  BangumiSubjectsCompanion copyWith({
    Value<String>? subjectId,
    Value<String>? name,
    Value<String>? nameCn,
    Value<String>? summary,
    Value<String?>? imageUrl,
    Value<int?>? eps,
    Value<DateTime>? updatedAt,
    Value<int>? rowid,
  }) {
    return BangumiSubjectsCompanion(
      subjectId: subjectId ?? this.subjectId,
      name: name ?? this.name,
      nameCn: nameCn ?? this.nameCn,
      summary: summary ?? this.summary,
      imageUrl: imageUrl ?? this.imageUrl,
      eps: eps ?? this.eps,
      updatedAt: updatedAt ?? this.updatedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (subjectId.present) {
      map['subject_id'] = Variable<String>(subjectId.value);
    }
    if (name.present) {
      map['name'] = Variable<String>(name.value);
    }
    if (nameCn.present) {
      map['name_cn'] = Variable<String>(nameCn.value);
    }
    if (summary.present) {
      map['summary'] = Variable<String>(summary.value);
    }
    if (imageUrl.present) {
      map['image_url'] = Variable<String>(imageUrl.value);
    }
    if (eps.present) {
      map['eps'] = Variable<int>(eps.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('BangumiSubjectsCompanion(')
          ..write('subjectId: $subjectId, ')
          ..write('name: $name, ')
          ..write('nameCn: $nameCn, ')
          ..write('summary: $summary, ')
          ..write('imageUrl: $imageUrl, ')
          ..write('eps: $eps, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $BangumiCollectionsTable extends BangumiCollections
    with TableInfo<$BangumiCollectionsTable, BangumiCollectionRecord> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $BangumiCollectionsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _accountIdMeta = const VerificationMeta(
    'accountId',
  );
  @override
  late final GeneratedColumn<String> accountId = GeneratedColumn<String>(
    'account_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES bangumi_accounts (account_id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _subjectIdMeta = const VerificationMeta(
    'subjectId',
  );
  @override
  late final GeneratedColumn<String> subjectId = GeneratedColumn<String>(
    'subject_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES bangumi_subjects (subject_id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _statusMeta = const VerificationMeta('status');
  @override
  late final GeneratedColumn<int> status = GeneratedColumn<int>(
    'status',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _remoteRevisionMeta = const VerificationMeta(
    'remoteRevision',
  );
  @override
  late final GeneratedColumn<String> remoteRevision = GeneratedColumn<String>(
    'remote_revision',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _localUpdatedAtMeta = const VerificationMeta(
    'localUpdatedAt',
  );
  @override
  late final GeneratedColumn<DateTime> localUpdatedAt =
      GeneratedColumn<DateTime>(
        'local_updated_at',
        aliasedName,
        false,
        type: DriftSqlType.dateTime,
        requiredDuringInsert: true,
      );
  static const VerificationMeta _remoteUpdatedAtMeta = const VerificationMeta(
    'remoteUpdatedAt',
  );
  @override
  late final GeneratedColumn<DateTime> remoteUpdatedAt =
      GeneratedColumn<DateTime>(
        'remote_updated_at',
        aliasedName,
        true,
        type: DriftSqlType.dateTime,
        requiredDuringInsert: false,
      );
  @override
  List<GeneratedColumn> get $columns => [
    accountId,
    subjectId,
    status,
    remoteRevision,
    localUpdatedAt,
    remoteUpdatedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'bangumi_collections';
  @override
  VerificationContext validateIntegrity(
    Insertable<BangumiCollectionRecord> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('account_id')) {
      context.handle(
        _accountIdMeta,
        accountId.isAcceptableOrUnknown(data['account_id']!, _accountIdMeta),
      );
    } else if (isInserting) {
      context.missing(_accountIdMeta);
    }
    if (data.containsKey('subject_id')) {
      context.handle(
        _subjectIdMeta,
        subjectId.isAcceptableOrUnknown(data['subject_id']!, _subjectIdMeta),
      );
    } else if (isInserting) {
      context.missing(_subjectIdMeta);
    }
    if (data.containsKey('status')) {
      context.handle(
        _statusMeta,
        status.isAcceptableOrUnknown(data['status']!, _statusMeta),
      );
    }
    if (data.containsKey('remote_revision')) {
      context.handle(
        _remoteRevisionMeta,
        remoteRevision.isAcceptableOrUnknown(
          data['remote_revision']!,
          _remoteRevisionMeta,
        ),
      );
    }
    if (data.containsKey('local_updated_at')) {
      context.handle(
        _localUpdatedAtMeta,
        localUpdatedAt.isAcceptableOrUnknown(
          data['local_updated_at']!,
          _localUpdatedAtMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_localUpdatedAtMeta);
    }
    if (data.containsKey('remote_updated_at')) {
      context.handle(
        _remoteUpdatedAtMeta,
        remoteUpdatedAt.isAcceptableOrUnknown(
          data['remote_updated_at']!,
          _remoteUpdatedAtMeta,
        ),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {accountId, subjectId};
  @override
  BangumiCollectionRecord map(
    Map<String, dynamic> data, {
    String? tablePrefix,
  }) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return BangumiCollectionRecord(
      accountId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}account_id'],
      )!,
      subjectId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}subject_id'],
      )!,
      status: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}status'],
      ),
      remoteRevision: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}remote_revision'],
      ),
      localUpdatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}local_updated_at'],
      )!,
      remoteUpdatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}remote_updated_at'],
      ),
    );
  }

  @override
  $BangumiCollectionsTable createAlias(String alias) {
    return $BangumiCollectionsTable(attachedDatabase, alias);
  }
}

class BangumiCollectionRecord extends DataClass
    implements Insertable<BangumiCollectionRecord> {
  final String accountId;
  final String subjectId;
  final int? status;
  final String? remoteRevision;
  final DateTime localUpdatedAt;
  final DateTime? remoteUpdatedAt;
  const BangumiCollectionRecord({
    required this.accountId,
    required this.subjectId,
    this.status,
    this.remoteRevision,
    required this.localUpdatedAt,
    this.remoteUpdatedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['account_id'] = Variable<String>(accountId);
    map['subject_id'] = Variable<String>(subjectId);
    if (!nullToAbsent || status != null) {
      map['status'] = Variable<int>(status);
    }
    if (!nullToAbsent || remoteRevision != null) {
      map['remote_revision'] = Variable<String>(remoteRevision);
    }
    map['local_updated_at'] = Variable<DateTime>(localUpdatedAt);
    if (!nullToAbsent || remoteUpdatedAt != null) {
      map['remote_updated_at'] = Variable<DateTime>(remoteUpdatedAt);
    }
    return map;
  }

  BangumiCollectionsCompanion toCompanion(bool nullToAbsent) {
    return BangumiCollectionsCompanion(
      accountId: Value(accountId),
      subjectId: Value(subjectId),
      status: status == null && nullToAbsent
          ? const Value.absent()
          : Value(status),
      remoteRevision: remoteRevision == null && nullToAbsent
          ? const Value.absent()
          : Value(remoteRevision),
      localUpdatedAt: Value(localUpdatedAt),
      remoteUpdatedAt: remoteUpdatedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(remoteUpdatedAt),
    );
  }

  factory BangumiCollectionRecord.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return BangumiCollectionRecord(
      accountId: serializer.fromJson<String>(json['accountId']),
      subjectId: serializer.fromJson<String>(json['subjectId']),
      status: serializer.fromJson<int?>(json['status']),
      remoteRevision: serializer.fromJson<String?>(json['remoteRevision']),
      localUpdatedAt: serializer.fromJson<DateTime>(json['localUpdatedAt']),
      remoteUpdatedAt: serializer.fromJson<DateTime?>(json['remoteUpdatedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'accountId': serializer.toJson<String>(accountId),
      'subjectId': serializer.toJson<String>(subjectId),
      'status': serializer.toJson<int?>(status),
      'remoteRevision': serializer.toJson<String?>(remoteRevision),
      'localUpdatedAt': serializer.toJson<DateTime>(localUpdatedAt),
      'remoteUpdatedAt': serializer.toJson<DateTime?>(remoteUpdatedAt),
    };
  }

  BangumiCollectionRecord copyWith({
    String? accountId,
    String? subjectId,
    Value<int?> status = const Value.absent(),
    Value<String?> remoteRevision = const Value.absent(),
    DateTime? localUpdatedAt,
    Value<DateTime?> remoteUpdatedAt = const Value.absent(),
  }) => BangumiCollectionRecord(
    accountId: accountId ?? this.accountId,
    subjectId: subjectId ?? this.subjectId,
    status: status.present ? status.value : this.status,
    remoteRevision: remoteRevision.present
        ? remoteRevision.value
        : this.remoteRevision,
    localUpdatedAt: localUpdatedAt ?? this.localUpdatedAt,
    remoteUpdatedAt: remoteUpdatedAt.present
        ? remoteUpdatedAt.value
        : this.remoteUpdatedAt,
  );
  BangumiCollectionRecord copyWithCompanion(BangumiCollectionsCompanion data) {
    return BangumiCollectionRecord(
      accountId: data.accountId.present ? data.accountId.value : this.accountId,
      subjectId: data.subjectId.present ? data.subjectId.value : this.subjectId,
      status: data.status.present ? data.status.value : this.status,
      remoteRevision: data.remoteRevision.present
          ? data.remoteRevision.value
          : this.remoteRevision,
      localUpdatedAt: data.localUpdatedAt.present
          ? data.localUpdatedAt.value
          : this.localUpdatedAt,
      remoteUpdatedAt: data.remoteUpdatedAt.present
          ? data.remoteUpdatedAt.value
          : this.remoteUpdatedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('BangumiCollectionRecord(')
          ..write('accountId: $accountId, ')
          ..write('subjectId: $subjectId, ')
          ..write('status: $status, ')
          ..write('remoteRevision: $remoteRevision, ')
          ..write('localUpdatedAt: $localUpdatedAt, ')
          ..write('remoteUpdatedAt: $remoteUpdatedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    accountId,
    subjectId,
    status,
    remoteRevision,
    localUpdatedAt,
    remoteUpdatedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is BangumiCollectionRecord &&
          other.accountId == this.accountId &&
          other.subjectId == this.subjectId &&
          other.status == this.status &&
          other.remoteRevision == this.remoteRevision &&
          other.localUpdatedAt == this.localUpdatedAt &&
          other.remoteUpdatedAt == this.remoteUpdatedAt);
}

class BangumiCollectionsCompanion
    extends UpdateCompanion<BangumiCollectionRecord> {
  final Value<String> accountId;
  final Value<String> subjectId;
  final Value<int?> status;
  final Value<String?> remoteRevision;
  final Value<DateTime> localUpdatedAt;
  final Value<DateTime?> remoteUpdatedAt;
  final Value<int> rowid;
  const BangumiCollectionsCompanion({
    this.accountId = const Value.absent(),
    this.subjectId = const Value.absent(),
    this.status = const Value.absent(),
    this.remoteRevision = const Value.absent(),
    this.localUpdatedAt = const Value.absent(),
    this.remoteUpdatedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  BangumiCollectionsCompanion.insert({
    required String accountId,
    required String subjectId,
    this.status = const Value.absent(),
    this.remoteRevision = const Value.absent(),
    required DateTime localUpdatedAt,
    this.remoteUpdatedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : accountId = Value(accountId),
       subjectId = Value(subjectId),
       localUpdatedAt = Value(localUpdatedAt);
  static Insertable<BangumiCollectionRecord> custom({
    Expression<String>? accountId,
    Expression<String>? subjectId,
    Expression<int>? status,
    Expression<String>? remoteRevision,
    Expression<DateTime>? localUpdatedAt,
    Expression<DateTime>? remoteUpdatedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (accountId != null) 'account_id': accountId,
      if (subjectId != null) 'subject_id': subjectId,
      if (status != null) 'status': status,
      if (remoteRevision != null) 'remote_revision': remoteRevision,
      if (localUpdatedAt != null) 'local_updated_at': localUpdatedAt,
      if (remoteUpdatedAt != null) 'remote_updated_at': remoteUpdatedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  BangumiCollectionsCompanion copyWith({
    Value<String>? accountId,
    Value<String>? subjectId,
    Value<int?>? status,
    Value<String?>? remoteRevision,
    Value<DateTime>? localUpdatedAt,
    Value<DateTime?>? remoteUpdatedAt,
    Value<int>? rowid,
  }) {
    return BangumiCollectionsCompanion(
      accountId: accountId ?? this.accountId,
      subjectId: subjectId ?? this.subjectId,
      status: status ?? this.status,
      remoteRevision: remoteRevision ?? this.remoteRevision,
      localUpdatedAt: localUpdatedAt ?? this.localUpdatedAt,
      remoteUpdatedAt: remoteUpdatedAt ?? this.remoteUpdatedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (accountId.present) {
      map['account_id'] = Variable<String>(accountId.value);
    }
    if (subjectId.present) {
      map['subject_id'] = Variable<String>(subjectId.value);
    }
    if (status.present) {
      map['status'] = Variable<int>(status.value);
    }
    if (remoteRevision.present) {
      map['remote_revision'] = Variable<String>(remoteRevision.value);
    }
    if (localUpdatedAt.present) {
      map['local_updated_at'] = Variable<DateTime>(localUpdatedAt.value);
    }
    if (remoteUpdatedAt.present) {
      map['remote_updated_at'] = Variable<DateTime>(remoteUpdatedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('BangumiCollectionsCompanion(')
          ..write('accountId: $accountId, ')
          ..write('subjectId: $subjectId, ')
          ..write('status: $status, ')
          ..write('remoteRevision: $remoteRevision, ')
          ..write('localUpdatedAt: $localUpdatedAt, ')
          ..write('remoteUpdatedAt: $remoteUpdatedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $BangumiEpisodesTable extends BangumiEpisodes
    with TableInfo<$BangumiEpisodesTable, BangumiEpisodeRecord> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $BangumiEpisodesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _episodeIdMeta = const VerificationMeta(
    'episodeId',
  );
  @override
  late final GeneratedColumn<String> episodeId = GeneratedColumn<String>(
    'episode_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _subjectIdMeta = const VerificationMeta(
    'subjectId',
  );
  @override
  late final GeneratedColumn<String> subjectId = GeneratedColumn<String>(
    'subject_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES bangumi_subjects (subject_id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _nameMeta = const VerificationMeta('name');
  @override
  late final GeneratedColumn<String> name = GeneratedColumn<String>(
    'name',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _nameCnMeta = const VerificationMeta('nameCn');
  @override
  late final GeneratedColumn<String> nameCn = GeneratedColumn<String>(
    'name_cn',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _sortMeta = const VerificationMeta('sort');
  @override
  late final GeneratedColumn<double> sort = GeneratedColumn<double>(
    'sort',
    aliasedName,
    false,
    type: DriftSqlType.double,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _typeMeta = const VerificationMeta('type');
  @override
  late final GeneratedColumn<int> type = GeneratedColumn<int>(
    'type',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _durationMeta = const VerificationMeta(
    'duration',
  );
  @override
  late final GeneratedColumn<int> duration = GeneratedColumn<int>(
    'duration',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  @override
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
    'updated_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [
    episodeId,
    subjectId,
    name,
    nameCn,
    sort,
    type,
    duration,
    updatedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'bangumi_episodes';
  @override
  VerificationContext validateIntegrity(
    Insertable<BangumiEpisodeRecord> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('episode_id')) {
      context.handle(
        _episodeIdMeta,
        episodeId.isAcceptableOrUnknown(data['episode_id']!, _episodeIdMeta),
      );
    } else if (isInserting) {
      context.missing(_episodeIdMeta);
    }
    if (data.containsKey('subject_id')) {
      context.handle(
        _subjectIdMeta,
        subjectId.isAcceptableOrUnknown(data['subject_id']!, _subjectIdMeta),
      );
    } else if (isInserting) {
      context.missing(_subjectIdMeta);
    }
    if (data.containsKey('name')) {
      context.handle(
        _nameMeta,
        name.isAcceptableOrUnknown(data['name']!, _nameMeta),
      );
    } else if (isInserting) {
      context.missing(_nameMeta);
    }
    if (data.containsKey('name_cn')) {
      context.handle(
        _nameCnMeta,
        nameCn.isAcceptableOrUnknown(data['name_cn']!, _nameCnMeta),
      );
    } else if (isInserting) {
      context.missing(_nameCnMeta);
    }
    if (data.containsKey('sort')) {
      context.handle(
        _sortMeta,
        sort.isAcceptableOrUnknown(data['sort']!, _sortMeta),
      );
    } else if (isInserting) {
      context.missing(_sortMeta);
    }
    if (data.containsKey('type')) {
      context.handle(
        _typeMeta,
        type.isAcceptableOrUnknown(data['type']!, _typeMeta),
      );
    } else if (isInserting) {
      context.missing(_typeMeta);
    }
    if (data.containsKey('duration')) {
      context.handle(
        _durationMeta,
        duration.isAcceptableOrUnknown(data['duration']!, _durationMeta),
      );
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_updatedAtMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {episodeId};
  @override
  BangumiEpisodeRecord map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return BangumiEpisodeRecord(
      episodeId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}episode_id'],
      )!,
      subjectId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}subject_id'],
      )!,
      name: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}name'],
      )!,
      nameCn: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}name_cn'],
      )!,
      sort: attachedDatabase.typeMapping.read(
        DriftSqlType.double,
        data['${effectivePrefix}sort'],
      )!,
      type: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}type'],
      )!,
      duration: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}duration'],
      ),
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      )!,
    );
  }

  @override
  $BangumiEpisodesTable createAlias(String alias) {
    return $BangumiEpisodesTable(attachedDatabase, alias);
  }
}

class BangumiEpisodeRecord extends DataClass
    implements Insertable<BangumiEpisodeRecord> {
  final String episodeId;
  final String subjectId;
  final String name;
  final String nameCn;
  final double sort;
  final int type;
  final int? duration;
  final DateTime updatedAt;
  const BangumiEpisodeRecord({
    required this.episodeId,
    required this.subjectId,
    required this.name,
    required this.nameCn,
    required this.sort,
    required this.type,
    this.duration,
    required this.updatedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['episode_id'] = Variable<String>(episodeId);
    map['subject_id'] = Variable<String>(subjectId);
    map['name'] = Variable<String>(name);
    map['name_cn'] = Variable<String>(nameCn);
    map['sort'] = Variable<double>(sort);
    map['type'] = Variable<int>(type);
    if (!nullToAbsent || duration != null) {
      map['duration'] = Variable<int>(duration);
    }
    map['updated_at'] = Variable<DateTime>(updatedAt);
    return map;
  }

  BangumiEpisodesCompanion toCompanion(bool nullToAbsent) {
    return BangumiEpisodesCompanion(
      episodeId: Value(episodeId),
      subjectId: Value(subjectId),
      name: Value(name),
      nameCn: Value(nameCn),
      sort: Value(sort),
      type: Value(type),
      duration: duration == null && nullToAbsent
          ? const Value.absent()
          : Value(duration),
      updatedAt: Value(updatedAt),
    );
  }

  factory BangumiEpisodeRecord.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return BangumiEpisodeRecord(
      episodeId: serializer.fromJson<String>(json['episodeId']),
      subjectId: serializer.fromJson<String>(json['subjectId']),
      name: serializer.fromJson<String>(json['name']),
      nameCn: serializer.fromJson<String>(json['nameCn']),
      sort: serializer.fromJson<double>(json['sort']),
      type: serializer.fromJson<int>(json['type']),
      duration: serializer.fromJson<int?>(json['duration']),
      updatedAt: serializer.fromJson<DateTime>(json['updatedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'episodeId': serializer.toJson<String>(episodeId),
      'subjectId': serializer.toJson<String>(subjectId),
      'name': serializer.toJson<String>(name),
      'nameCn': serializer.toJson<String>(nameCn),
      'sort': serializer.toJson<double>(sort),
      'type': serializer.toJson<int>(type),
      'duration': serializer.toJson<int?>(duration),
      'updatedAt': serializer.toJson<DateTime>(updatedAt),
    };
  }

  BangumiEpisodeRecord copyWith({
    String? episodeId,
    String? subjectId,
    String? name,
    String? nameCn,
    double? sort,
    int? type,
    Value<int?> duration = const Value.absent(),
    DateTime? updatedAt,
  }) => BangumiEpisodeRecord(
    episodeId: episodeId ?? this.episodeId,
    subjectId: subjectId ?? this.subjectId,
    name: name ?? this.name,
    nameCn: nameCn ?? this.nameCn,
    sort: sort ?? this.sort,
    type: type ?? this.type,
    duration: duration.present ? duration.value : this.duration,
    updatedAt: updatedAt ?? this.updatedAt,
  );
  BangumiEpisodeRecord copyWithCompanion(BangumiEpisodesCompanion data) {
    return BangumiEpisodeRecord(
      episodeId: data.episodeId.present ? data.episodeId.value : this.episodeId,
      subjectId: data.subjectId.present ? data.subjectId.value : this.subjectId,
      name: data.name.present ? data.name.value : this.name,
      nameCn: data.nameCn.present ? data.nameCn.value : this.nameCn,
      sort: data.sort.present ? data.sort.value : this.sort,
      type: data.type.present ? data.type.value : this.type,
      duration: data.duration.present ? data.duration.value : this.duration,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('BangumiEpisodeRecord(')
          ..write('episodeId: $episodeId, ')
          ..write('subjectId: $subjectId, ')
          ..write('name: $name, ')
          ..write('nameCn: $nameCn, ')
          ..write('sort: $sort, ')
          ..write('type: $type, ')
          ..write('duration: $duration, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    episodeId,
    subjectId,
    name,
    nameCn,
    sort,
    type,
    duration,
    updatedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is BangumiEpisodeRecord &&
          other.episodeId == this.episodeId &&
          other.subjectId == this.subjectId &&
          other.name == this.name &&
          other.nameCn == this.nameCn &&
          other.sort == this.sort &&
          other.type == this.type &&
          other.duration == this.duration &&
          other.updatedAt == this.updatedAt);
}

class BangumiEpisodesCompanion extends UpdateCompanion<BangumiEpisodeRecord> {
  final Value<String> episodeId;
  final Value<String> subjectId;
  final Value<String> name;
  final Value<String> nameCn;
  final Value<double> sort;
  final Value<int> type;
  final Value<int?> duration;
  final Value<DateTime> updatedAt;
  final Value<int> rowid;
  const BangumiEpisodesCompanion({
    this.episodeId = const Value.absent(),
    this.subjectId = const Value.absent(),
    this.name = const Value.absent(),
    this.nameCn = const Value.absent(),
    this.sort = const Value.absent(),
    this.type = const Value.absent(),
    this.duration = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  BangumiEpisodesCompanion.insert({
    required String episodeId,
    required String subjectId,
    required String name,
    required String nameCn,
    required double sort,
    required int type,
    this.duration = const Value.absent(),
    required DateTime updatedAt,
    this.rowid = const Value.absent(),
  }) : episodeId = Value(episodeId),
       subjectId = Value(subjectId),
       name = Value(name),
       nameCn = Value(nameCn),
       sort = Value(sort),
       type = Value(type),
       updatedAt = Value(updatedAt);
  static Insertable<BangumiEpisodeRecord> custom({
    Expression<String>? episodeId,
    Expression<String>? subjectId,
    Expression<String>? name,
    Expression<String>? nameCn,
    Expression<double>? sort,
    Expression<int>? type,
    Expression<int>? duration,
    Expression<DateTime>? updatedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (episodeId != null) 'episode_id': episodeId,
      if (subjectId != null) 'subject_id': subjectId,
      if (name != null) 'name': name,
      if (nameCn != null) 'name_cn': nameCn,
      if (sort != null) 'sort': sort,
      if (type != null) 'type': type,
      if (duration != null) 'duration': duration,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  BangumiEpisodesCompanion copyWith({
    Value<String>? episodeId,
    Value<String>? subjectId,
    Value<String>? name,
    Value<String>? nameCn,
    Value<double>? sort,
    Value<int>? type,
    Value<int?>? duration,
    Value<DateTime>? updatedAt,
    Value<int>? rowid,
  }) {
    return BangumiEpisodesCompanion(
      episodeId: episodeId ?? this.episodeId,
      subjectId: subjectId ?? this.subjectId,
      name: name ?? this.name,
      nameCn: nameCn ?? this.nameCn,
      sort: sort ?? this.sort,
      type: type ?? this.type,
      duration: duration ?? this.duration,
      updatedAt: updatedAt ?? this.updatedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (episodeId.present) {
      map['episode_id'] = Variable<String>(episodeId.value);
    }
    if (subjectId.present) {
      map['subject_id'] = Variable<String>(subjectId.value);
    }
    if (name.present) {
      map['name'] = Variable<String>(name.value);
    }
    if (nameCn.present) {
      map['name_cn'] = Variable<String>(nameCn.value);
    }
    if (sort.present) {
      map['sort'] = Variable<double>(sort.value);
    }
    if (type.present) {
      map['type'] = Variable<int>(type.value);
    }
    if (duration.present) {
      map['duration'] = Variable<int>(duration.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('BangumiEpisodesCompanion(')
          ..write('episodeId: $episodeId, ')
          ..write('subjectId: $subjectId, ')
          ..write('name: $name, ')
          ..write('nameCn: $nameCn, ')
          ..write('sort: $sort, ')
          ..write('type: $type, ')
          ..write('duration: $duration, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $BangumiEpisodeCollectionsTable extends BangumiEpisodeCollections
    with
        TableInfo<
          $BangumiEpisodeCollectionsTable,
          BangumiEpisodeCollectionRecord
        > {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $BangumiEpisodeCollectionsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _accountIdMeta = const VerificationMeta(
    'accountId',
  );
  @override
  late final GeneratedColumn<String> accountId = GeneratedColumn<String>(
    'account_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES bangumi_accounts (account_id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _episodeIdMeta = const VerificationMeta(
    'episodeId',
  );
  @override
  late final GeneratedColumn<String> episodeId = GeneratedColumn<String>(
    'episode_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES bangumi_episodes (episode_id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _watchedMeta = const VerificationMeta(
    'watched',
  );
  @override
  late final GeneratedColumn<bool> watched = GeneratedColumn<bool>(
    'watched',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("watched" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _remoteRevisionMeta = const VerificationMeta(
    'remoteRevision',
  );
  @override
  late final GeneratedColumn<String> remoteRevision = GeneratedColumn<String>(
    'remote_revision',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _localUpdatedAtMeta = const VerificationMeta(
    'localUpdatedAt',
  );
  @override
  late final GeneratedColumn<DateTime> localUpdatedAt =
      GeneratedColumn<DateTime>(
        'local_updated_at',
        aliasedName,
        false,
        type: DriftSqlType.dateTime,
        requiredDuringInsert: true,
      );
  static const VerificationMeta _remoteUpdatedAtMeta = const VerificationMeta(
    'remoteUpdatedAt',
  );
  @override
  late final GeneratedColumn<DateTime> remoteUpdatedAt =
      GeneratedColumn<DateTime>(
        'remote_updated_at',
        aliasedName,
        true,
        type: DriftSqlType.dateTime,
        requiredDuringInsert: false,
      );
  @override
  List<GeneratedColumn> get $columns => [
    accountId,
    episodeId,
    watched,
    remoteRevision,
    localUpdatedAt,
    remoteUpdatedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'bangumi_episode_collections';
  @override
  VerificationContext validateIntegrity(
    Insertable<BangumiEpisodeCollectionRecord> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('account_id')) {
      context.handle(
        _accountIdMeta,
        accountId.isAcceptableOrUnknown(data['account_id']!, _accountIdMeta),
      );
    } else if (isInserting) {
      context.missing(_accountIdMeta);
    }
    if (data.containsKey('episode_id')) {
      context.handle(
        _episodeIdMeta,
        episodeId.isAcceptableOrUnknown(data['episode_id']!, _episodeIdMeta),
      );
    } else if (isInserting) {
      context.missing(_episodeIdMeta);
    }
    if (data.containsKey('watched')) {
      context.handle(
        _watchedMeta,
        watched.isAcceptableOrUnknown(data['watched']!, _watchedMeta),
      );
    }
    if (data.containsKey('remote_revision')) {
      context.handle(
        _remoteRevisionMeta,
        remoteRevision.isAcceptableOrUnknown(
          data['remote_revision']!,
          _remoteRevisionMeta,
        ),
      );
    }
    if (data.containsKey('local_updated_at')) {
      context.handle(
        _localUpdatedAtMeta,
        localUpdatedAt.isAcceptableOrUnknown(
          data['local_updated_at']!,
          _localUpdatedAtMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_localUpdatedAtMeta);
    }
    if (data.containsKey('remote_updated_at')) {
      context.handle(
        _remoteUpdatedAtMeta,
        remoteUpdatedAt.isAcceptableOrUnknown(
          data['remote_updated_at']!,
          _remoteUpdatedAtMeta,
        ),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {accountId, episodeId};
  @override
  BangumiEpisodeCollectionRecord map(
    Map<String, dynamic> data, {
    String? tablePrefix,
  }) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return BangumiEpisodeCollectionRecord(
      accountId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}account_id'],
      )!,
      episodeId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}episode_id'],
      )!,
      watched: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}watched'],
      )!,
      remoteRevision: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}remote_revision'],
      ),
      localUpdatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}local_updated_at'],
      )!,
      remoteUpdatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}remote_updated_at'],
      ),
    );
  }

  @override
  $BangumiEpisodeCollectionsTable createAlias(String alias) {
    return $BangumiEpisodeCollectionsTable(attachedDatabase, alias);
  }
}

class BangumiEpisodeCollectionRecord extends DataClass
    implements Insertable<BangumiEpisodeCollectionRecord> {
  final String accountId;
  final String episodeId;
  final bool watched;
  final String? remoteRevision;
  final DateTime localUpdatedAt;
  final DateTime? remoteUpdatedAt;
  const BangumiEpisodeCollectionRecord({
    required this.accountId,
    required this.episodeId,
    required this.watched,
    this.remoteRevision,
    required this.localUpdatedAt,
    this.remoteUpdatedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['account_id'] = Variable<String>(accountId);
    map['episode_id'] = Variable<String>(episodeId);
    map['watched'] = Variable<bool>(watched);
    if (!nullToAbsent || remoteRevision != null) {
      map['remote_revision'] = Variable<String>(remoteRevision);
    }
    map['local_updated_at'] = Variable<DateTime>(localUpdatedAt);
    if (!nullToAbsent || remoteUpdatedAt != null) {
      map['remote_updated_at'] = Variable<DateTime>(remoteUpdatedAt);
    }
    return map;
  }

  BangumiEpisodeCollectionsCompanion toCompanion(bool nullToAbsent) {
    return BangumiEpisodeCollectionsCompanion(
      accountId: Value(accountId),
      episodeId: Value(episodeId),
      watched: Value(watched),
      remoteRevision: remoteRevision == null && nullToAbsent
          ? const Value.absent()
          : Value(remoteRevision),
      localUpdatedAt: Value(localUpdatedAt),
      remoteUpdatedAt: remoteUpdatedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(remoteUpdatedAt),
    );
  }

  factory BangumiEpisodeCollectionRecord.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return BangumiEpisodeCollectionRecord(
      accountId: serializer.fromJson<String>(json['accountId']),
      episodeId: serializer.fromJson<String>(json['episodeId']),
      watched: serializer.fromJson<bool>(json['watched']),
      remoteRevision: serializer.fromJson<String?>(json['remoteRevision']),
      localUpdatedAt: serializer.fromJson<DateTime>(json['localUpdatedAt']),
      remoteUpdatedAt: serializer.fromJson<DateTime?>(json['remoteUpdatedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'accountId': serializer.toJson<String>(accountId),
      'episodeId': serializer.toJson<String>(episodeId),
      'watched': serializer.toJson<bool>(watched),
      'remoteRevision': serializer.toJson<String?>(remoteRevision),
      'localUpdatedAt': serializer.toJson<DateTime>(localUpdatedAt),
      'remoteUpdatedAt': serializer.toJson<DateTime?>(remoteUpdatedAt),
    };
  }

  BangumiEpisodeCollectionRecord copyWith({
    String? accountId,
    String? episodeId,
    bool? watched,
    Value<String?> remoteRevision = const Value.absent(),
    DateTime? localUpdatedAt,
    Value<DateTime?> remoteUpdatedAt = const Value.absent(),
  }) => BangumiEpisodeCollectionRecord(
    accountId: accountId ?? this.accountId,
    episodeId: episodeId ?? this.episodeId,
    watched: watched ?? this.watched,
    remoteRevision: remoteRevision.present
        ? remoteRevision.value
        : this.remoteRevision,
    localUpdatedAt: localUpdatedAt ?? this.localUpdatedAt,
    remoteUpdatedAt: remoteUpdatedAt.present
        ? remoteUpdatedAt.value
        : this.remoteUpdatedAt,
  );
  BangumiEpisodeCollectionRecord copyWithCompanion(
    BangumiEpisodeCollectionsCompanion data,
  ) {
    return BangumiEpisodeCollectionRecord(
      accountId: data.accountId.present ? data.accountId.value : this.accountId,
      episodeId: data.episodeId.present ? data.episodeId.value : this.episodeId,
      watched: data.watched.present ? data.watched.value : this.watched,
      remoteRevision: data.remoteRevision.present
          ? data.remoteRevision.value
          : this.remoteRevision,
      localUpdatedAt: data.localUpdatedAt.present
          ? data.localUpdatedAt.value
          : this.localUpdatedAt,
      remoteUpdatedAt: data.remoteUpdatedAt.present
          ? data.remoteUpdatedAt.value
          : this.remoteUpdatedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('BangumiEpisodeCollectionRecord(')
          ..write('accountId: $accountId, ')
          ..write('episodeId: $episodeId, ')
          ..write('watched: $watched, ')
          ..write('remoteRevision: $remoteRevision, ')
          ..write('localUpdatedAt: $localUpdatedAt, ')
          ..write('remoteUpdatedAt: $remoteUpdatedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    accountId,
    episodeId,
    watched,
    remoteRevision,
    localUpdatedAt,
    remoteUpdatedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is BangumiEpisodeCollectionRecord &&
          other.accountId == this.accountId &&
          other.episodeId == this.episodeId &&
          other.watched == this.watched &&
          other.remoteRevision == this.remoteRevision &&
          other.localUpdatedAt == this.localUpdatedAt &&
          other.remoteUpdatedAt == this.remoteUpdatedAt);
}

class BangumiEpisodeCollectionsCompanion
    extends UpdateCompanion<BangumiEpisodeCollectionRecord> {
  final Value<String> accountId;
  final Value<String> episodeId;
  final Value<bool> watched;
  final Value<String?> remoteRevision;
  final Value<DateTime> localUpdatedAt;
  final Value<DateTime?> remoteUpdatedAt;
  final Value<int> rowid;
  const BangumiEpisodeCollectionsCompanion({
    this.accountId = const Value.absent(),
    this.episodeId = const Value.absent(),
    this.watched = const Value.absent(),
    this.remoteRevision = const Value.absent(),
    this.localUpdatedAt = const Value.absent(),
    this.remoteUpdatedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  BangumiEpisodeCollectionsCompanion.insert({
    required String accountId,
    required String episodeId,
    this.watched = const Value.absent(),
    this.remoteRevision = const Value.absent(),
    required DateTime localUpdatedAt,
    this.remoteUpdatedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : accountId = Value(accountId),
       episodeId = Value(episodeId),
       localUpdatedAt = Value(localUpdatedAt);
  static Insertable<BangumiEpisodeCollectionRecord> custom({
    Expression<String>? accountId,
    Expression<String>? episodeId,
    Expression<bool>? watched,
    Expression<String>? remoteRevision,
    Expression<DateTime>? localUpdatedAt,
    Expression<DateTime>? remoteUpdatedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (accountId != null) 'account_id': accountId,
      if (episodeId != null) 'episode_id': episodeId,
      if (watched != null) 'watched': watched,
      if (remoteRevision != null) 'remote_revision': remoteRevision,
      if (localUpdatedAt != null) 'local_updated_at': localUpdatedAt,
      if (remoteUpdatedAt != null) 'remote_updated_at': remoteUpdatedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  BangumiEpisodeCollectionsCompanion copyWith({
    Value<String>? accountId,
    Value<String>? episodeId,
    Value<bool>? watched,
    Value<String?>? remoteRevision,
    Value<DateTime>? localUpdatedAt,
    Value<DateTime?>? remoteUpdatedAt,
    Value<int>? rowid,
  }) {
    return BangumiEpisodeCollectionsCompanion(
      accountId: accountId ?? this.accountId,
      episodeId: episodeId ?? this.episodeId,
      watched: watched ?? this.watched,
      remoteRevision: remoteRevision ?? this.remoteRevision,
      localUpdatedAt: localUpdatedAt ?? this.localUpdatedAt,
      remoteUpdatedAt: remoteUpdatedAt ?? this.remoteUpdatedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (accountId.present) {
      map['account_id'] = Variable<String>(accountId.value);
    }
    if (episodeId.present) {
      map['episode_id'] = Variable<String>(episodeId.value);
    }
    if (watched.present) {
      map['watched'] = Variable<bool>(watched.value);
    }
    if (remoteRevision.present) {
      map['remote_revision'] = Variable<String>(remoteRevision.value);
    }
    if (localUpdatedAt.present) {
      map['local_updated_at'] = Variable<DateTime>(localUpdatedAt.value);
    }
    if (remoteUpdatedAt.present) {
      map['remote_updated_at'] = Variable<DateTime>(remoteUpdatedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('BangumiEpisodeCollectionsCompanion(')
          ..write('accountId: $accountId, ')
          ..write('episodeId: $episodeId, ')
          ..write('watched: $watched, ')
          ..write('remoteRevision: $remoteRevision, ')
          ..write('localUpdatedAt: $localUpdatedAt, ')
          ..write('remoteUpdatedAt: $remoteUpdatedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $BangumiMappingsTable extends BangumiMappings
    with TableInfo<$BangumiMappingsTable, BangumiMappingRecord> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $BangumiMappingsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _localSubjectKeyMeta = const VerificationMeta(
    'localSubjectKey',
  );
  @override
  late final GeneratedColumn<String> localSubjectKey = GeneratedColumn<String>(
    'local_subject_key',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _bangumiSubjectIdMeta = const VerificationMeta(
    'bangumiSubjectId',
  );
  @override
  late final GeneratedColumn<String> bangumiSubjectId = GeneratedColumn<String>(
    'bangumi_subject_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES bangumi_subjects (subject_id) ON DELETE RESTRICT',
    ),
  );
  static const VerificationMeta _confirmedAtMeta = const VerificationMeta(
    'confirmedAt',
  );
  @override
  late final GeneratedColumn<DateTime> confirmedAt = GeneratedColumn<DateTime>(
    'confirmed_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [
    localSubjectKey,
    bangumiSubjectId,
    confirmedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'bangumi_mappings';
  @override
  VerificationContext validateIntegrity(
    Insertable<BangumiMappingRecord> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('local_subject_key')) {
      context.handle(
        _localSubjectKeyMeta,
        localSubjectKey.isAcceptableOrUnknown(
          data['local_subject_key']!,
          _localSubjectKeyMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_localSubjectKeyMeta);
    }
    if (data.containsKey('bangumi_subject_id')) {
      context.handle(
        _bangumiSubjectIdMeta,
        bangumiSubjectId.isAcceptableOrUnknown(
          data['bangumi_subject_id']!,
          _bangumiSubjectIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_bangumiSubjectIdMeta);
    }
    if (data.containsKey('confirmed_at')) {
      context.handle(
        _confirmedAtMeta,
        confirmedAt.isAcceptableOrUnknown(
          data['confirmed_at']!,
          _confirmedAtMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_confirmedAtMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {localSubjectKey};
  @override
  BangumiMappingRecord map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return BangumiMappingRecord(
      localSubjectKey: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}local_subject_key'],
      )!,
      bangumiSubjectId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}bangumi_subject_id'],
      )!,
      confirmedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}confirmed_at'],
      )!,
    );
  }

  @override
  $BangumiMappingsTable createAlias(String alias) {
    return $BangumiMappingsTable(attachedDatabase, alias);
  }
}

class BangumiMappingRecord extends DataClass
    implements Insertable<BangumiMappingRecord> {
  final String localSubjectKey;
  final String bangumiSubjectId;
  final DateTime confirmedAt;
  const BangumiMappingRecord({
    required this.localSubjectKey,
    required this.bangumiSubjectId,
    required this.confirmedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['local_subject_key'] = Variable<String>(localSubjectKey);
    map['bangumi_subject_id'] = Variable<String>(bangumiSubjectId);
    map['confirmed_at'] = Variable<DateTime>(confirmedAt);
    return map;
  }

  BangumiMappingsCompanion toCompanion(bool nullToAbsent) {
    return BangumiMappingsCompanion(
      localSubjectKey: Value(localSubjectKey),
      bangumiSubjectId: Value(bangumiSubjectId),
      confirmedAt: Value(confirmedAt),
    );
  }

  factory BangumiMappingRecord.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return BangumiMappingRecord(
      localSubjectKey: serializer.fromJson<String>(json['localSubjectKey']),
      bangumiSubjectId: serializer.fromJson<String>(json['bangumiSubjectId']),
      confirmedAt: serializer.fromJson<DateTime>(json['confirmedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'localSubjectKey': serializer.toJson<String>(localSubjectKey),
      'bangumiSubjectId': serializer.toJson<String>(bangumiSubjectId),
      'confirmedAt': serializer.toJson<DateTime>(confirmedAt),
    };
  }

  BangumiMappingRecord copyWith({
    String? localSubjectKey,
    String? bangumiSubjectId,
    DateTime? confirmedAt,
  }) => BangumiMappingRecord(
    localSubjectKey: localSubjectKey ?? this.localSubjectKey,
    bangumiSubjectId: bangumiSubjectId ?? this.bangumiSubjectId,
    confirmedAt: confirmedAt ?? this.confirmedAt,
  );
  BangumiMappingRecord copyWithCompanion(BangumiMappingsCompanion data) {
    return BangumiMappingRecord(
      localSubjectKey: data.localSubjectKey.present
          ? data.localSubjectKey.value
          : this.localSubjectKey,
      bangumiSubjectId: data.bangumiSubjectId.present
          ? data.bangumiSubjectId.value
          : this.bangumiSubjectId,
      confirmedAt: data.confirmedAt.present
          ? data.confirmedAt.value
          : this.confirmedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('BangumiMappingRecord(')
          ..write('localSubjectKey: $localSubjectKey, ')
          ..write('bangumiSubjectId: $bangumiSubjectId, ')
          ..write('confirmedAt: $confirmedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode =>
      Object.hash(localSubjectKey, bangumiSubjectId, confirmedAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is BangumiMappingRecord &&
          other.localSubjectKey == this.localSubjectKey &&
          other.bangumiSubjectId == this.bangumiSubjectId &&
          other.confirmedAt == this.confirmedAt);
}

class BangumiMappingsCompanion extends UpdateCompanion<BangumiMappingRecord> {
  final Value<String> localSubjectKey;
  final Value<String> bangumiSubjectId;
  final Value<DateTime> confirmedAt;
  final Value<int> rowid;
  const BangumiMappingsCompanion({
    this.localSubjectKey = const Value.absent(),
    this.bangumiSubjectId = const Value.absent(),
    this.confirmedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  BangumiMappingsCompanion.insert({
    required String localSubjectKey,
    required String bangumiSubjectId,
    required DateTime confirmedAt,
    this.rowid = const Value.absent(),
  }) : localSubjectKey = Value(localSubjectKey),
       bangumiSubjectId = Value(bangumiSubjectId),
       confirmedAt = Value(confirmedAt);
  static Insertable<BangumiMappingRecord> custom({
    Expression<String>? localSubjectKey,
    Expression<String>? bangumiSubjectId,
    Expression<DateTime>? confirmedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (localSubjectKey != null) 'local_subject_key': localSubjectKey,
      if (bangumiSubjectId != null) 'bangumi_subject_id': bangumiSubjectId,
      if (confirmedAt != null) 'confirmed_at': confirmedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  BangumiMappingsCompanion copyWith({
    Value<String>? localSubjectKey,
    Value<String>? bangumiSubjectId,
    Value<DateTime>? confirmedAt,
    Value<int>? rowid,
  }) {
    return BangumiMappingsCompanion(
      localSubjectKey: localSubjectKey ?? this.localSubjectKey,
      bangumiSubjectId: bangumiSubjectId ?? this.bangumiSubjectId,
      confirmedAt: confirmedAt ?? this.confirmedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (localSubjectKey.present) {
      map['local_subject_key'] = Variable<String>(localSubjectKey.value);
    }
    if (bangumiSubjectId.present) {
      map['bangumi_subject_id'] = Variable<String>(bangumiSubjectId.value);
    }
    if (confirmedAt.present) {
      map['confirmed_at'] = Variable<DateTime>(confirmedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('BangumiMappingsCompanion(')
          ..write('localSubjectKey: $localSubjectKey, ')
          ..write('bangumiSubjectId: $bangumiSubjectId, ')
          ..write('confirmedAt: $confirmedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $BangumiSyncOperationsTable extends BangumiSyncOperations
    with TableInfo<$BangumiSyncOperationsTable, BangumiSyncOperationRecord> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $BangumiSyncOperationsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _operationIdMeta = const VerificationMeta(
    'operationId',
  );
  @override
  late final GeneratedColumn<String> operationId = GeneratedColumn<String>(
    'operation_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _accountIdMeta = const VerificationMeta(
    'accountId',
  );
  @override
  late final GeneratedColumn<String> accountId = GeneratedColumn<String>(
    'account_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES bangumi_accounts (account_id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _subjectIdMeta = const VerificationMeta(
    'subjectId',
  );
  @override
  late final GeneratedColumn<String> subjectId = GeneratedColumn<String>(
    'subject_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES bangumi_subjects (subject_id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _episodeIdMeta = const VerificationMeta(
    'episodeId',
  );
  @override
  late final GeneratedColumn<String> episodeId = GeneratedColumn<String>(
    'episode_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES bangumi_episodes (episode_id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _kindMeta = const VerificationMeta('kind');
  @override
  late final GeneratedColumn<String> kind = GeneratedColumn<String>(
    'kind',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _collectionStatusMeta = const VerificationMeta(
    'collectionStatus',
  );
  @override
  late final GeneratedColumn<int> collectionStatus = GeneratedColumn<int>(
    'collection_status',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _watchedMeta = const VerificationMeta(
    'watched',
  );
  @override
  late final GeneratedColumn<bool> watched = GeneratedColumn<bool>(
    'watched',
    aliasedName,
    true,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("watched" IN (0, 1))',
    ),
  );
  static const VerificationMeta _baseRemoteRevisionMeta =
      const VerificationMeta('baseRemoteRevision');
  @override
  late final GeneratedColumn<String> baseRemoteRevision =
      GeneratedColumn<String>(
        'base_remote_revision',
        aliasedName,
        true,
        type: DriftSqlType.string,
        requiredDuringInsert: false,
      );
  static const VerificationMeta _baseCollectionStatusMeta =
      const VerificationMeta('baseCollectionStatus');
  @override
  late final GeneratedColumn<int> baseCollectionStatus = GeneratedColumn<int>(
    'base_collection_status',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _baseWatchedMeta = const VerificationMeta(
    'baseWatched',
  );
  @override
  late final GeneratedColumn<bool> baseWatched = GeneratedColumn<bool>(
    'base_watched',
    aliasedName,
    true,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("base_watched" IN (0, 1))',
    ),
  );
  static const VerificationMeta _stateMeta = const VerificationMeta('state');
  @override
  late final GeneratedColumn<String> state = GeneratedColumn<String>(
    'state',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _attemptsMeta = const VerificationMeta(
    'attempts',
  );
  @override
  late final GeneratedColumn<int> attempts = GeneratedColumn<int>(
    'attempts',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  static const VerificationMeta _nextAttemptAtMeta = const VerificationMeta(
    'nextAttemptAt',
  );
  @override
  late final GeneratedColumn<DateTime> nextAttemptAt =
      GeneratedColumn<DateTime>(
        'next_attempt_at',
        aliasedName,
        true,
        type: DriftSqlType.dateTime,
        requiredDuringInsert: false,
      );
  static const VerificationMeta _lastErrorCodeMeta = const VerificationMeta(
    'lastErrorCode',
  );
  @override
  late final GeneratedColumn<String> lastErrorCode = GeneratedColumn<String>(
    'last_error_code',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _statusCodeMeta = const VerificationMeta(
    'statusCode',
  );
  @override
  late final GeneratedColumn<int> statusCode = GeneratedColumn<int>(
    'status_code',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  @override
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
    'updated_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [
    operationId,
    accountId,
    subjectId,
    episodeId,
    kind,
    collectionStatus,
    watched,
    baseRemoteRevision,
    baseCollectionStatus,
    baseWatched,
    state,
    attempts,
    nextAttemptAt,
    lastErrorCode,
    statusCode,
    createdAt,
    updatedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'bangumi_sync_operations';
  @override
  VerificationContext validateIntegrity(
    Insertable<BangumiSyncOperationRecord> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('operation_id')) {
      context.handle(
        _operationIdMeta,
        operationId.isAcceptableOrUnknown(
          data['operation_id']!,
          _operationIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_operationIdMeta);
    }
    if (data.containsKey('account_id')) {
      context.handle(
        _accountIdMeta,
        accountId.isAcceptableOrUnknown(data['account_id']!, _accountIdMeta),
      );
    } else if (isInserting) {
      context.missing(_accountIdMeta);
    }
    if (data.containsKey('subject_id')) {
      context.handle(
        _subjectIdMeta,
        subjectId.isAcceptableOrUnknown(data['subject_id']!, _subjectIdMeta),
      );
    } else if (isInserting) {
      context.missing(_subjectIdMeta);
    }
    if (data.containsKey('episode_id')) {
      context.handle(
        _episodeIdMeta,
        episodeId.isAcceptableOrUnknown(data['episode_id']!, _episodeIdMeta),
      );
    }
    if (data.containsKey('kind')) {
      context.handle(
        _kindMeta,
        kind.isAcceptableOrUnknown(data['kind']!, _kindMeta),
      );
    } else if (isInserting) {
      context.missing(_kindMeta);
    }
    if (data.containsKey('collection_status')) {
      context.handle(
        _collectionStatusMeta,
        collectionStatus.isAcceptableOrUnknown(
          data['collection_status']!,
          _collectionStatusMeta,
        ),
      );
    }
    if (data.containsKey('watched')) {
      context.handle(
        _watchedMeta,
        watched.isAcceptableOrUnknown(data['watched']!, _watchedMeta),
      );
    }
    if (data.containsKey('base_remote_revision')) {
      context.handle(
        _baseRemoteRevisionMeta,
        baseRemoteRevision.isAcceptableOrUnknown(
          data['base_remote_revision']!,
          _baseRemoteRevisionMeta,
        ),
      );
    }
    if (data.containsKey('base_collection_status')) {
      context.handle(
        _baseCollectionStatusMeta,
        baseCollectionStatus.isAcceptableOrUnknown(
          data['base_collection_status']!,
          _baseCollectionStatusMeta,
        ),
      );
    }
    if (data.containsKey('base_watched')) {
      context.handle(
        _baseWatchedMeta,
        baseWatched.isAcceptableOrUnknown(
          data['base_watched']!,
          _baseWatchedMeta,
        ),
      );
    }
    if (data.containsKey('state')) {
      context.handle(
        _stateMeta,
        state.isAcceptableOrUnknown(data['state']!, _stateMeta),
      );
    } else if (isInserting) {
      context.missing(_stateMeta);
    }
    if (data.containsKey('attempts')) {
      context.handle(
        _attemptsMeta,
        attempts.isAcceptableOrUnknown(data['attempts']!, _attemptsMeta),
      );
    }
    if (data.containsKey('next_attempt_at')) {
      context.handle(
        _nextAttemptAtMeta,
        nextAttemptAt.isAcceptableOrUnknown(
          data['next_attempt_at']!,
          _nextAttemptAtMeta,
        ),
      );
    }
    if (data.containsKey('last_error_code')) {
      context.handle(
        _lastErrorCodeMeta,
        lastErrorCode.isAcceptableOrUnknown(
          data['last_error_code']!,
          _lastErrorCodeMeta,
        ),
      );
    }
    if (data.containsKey('status_code')) {
      context.handle(
        _statusCodeMeta,
        statusCode.isAcceptableOrUnknown(data['status_code']!, _statusCodeMeta),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    } else if (isInserting) {
      context.missing(_createdAtMeta);
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_updatedAtMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {operationId};
  @override
  BangumiSyncOperationRecord map(
    Map<String, dynamic> data, {
    String? tablePrefix,
  }) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return BangumiSyncOperationRecord(
      operationId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}operation_id'],
      )!,
      accountId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}account_id'],
      )!,
      subjectId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}subject_id'],
      )!,
      episodeId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}episode_id'],
      ),
      kind: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}kind'],
      )!,
      collectionStatus: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}collection_status'],
      ),
      watched: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}watched'],
      ),
      baseRemoteRevision: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}base_remote_revision'],
      ),
      baseCollectionStatus: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}base_collection_status'],
      ),
      baseWatched: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}base_watched'],
      ),
      state: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}state'],
      )!,
      attempts: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}attempts'],
      )!,
      nextAttemptAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}next_attempt_at'],
      ),
      lastErrorCode: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}last_error_code'],
      ),
      statusCode: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}status_code'],
      ),
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      )!,
    );
  }

  @override
  $BangumiSyncOperationsTable createAlias(String alias) {
    return $BangumiSyncOperationsTable(attachedDatabase, alias);
  }
}

class BangumiSyncOperationRecord extends DataClass
    implements Insertable<BangumiSyncOperationRecord> {
  final String operationId;
  final String accountId;
  final String subjectId;
  final String? episodeId;
  final String kind;
  final int? collectionStatus;
  final bool? watched;
  final String? baseRemoteRevision;
  final int? baseCollectionStatus;
  final bool? baseWatched;
  final String state;
  final int attempts;
  final DateTime? nextAttemptAt;
  final String? lastErrorCode;
  final int? statusCode;
  final DateTime createdAt;
  final DateTime updatedAt;
  const BangumiSyncOperationRecord({
    required this.operationId,
    required this.accountId,
    required this.subjectId,
    this.episodeId,
    required this.kind,
    this.collectionStatus,
    this.watched,
    this.baseRemoteRevision,
    this.baseCollectionStatus,
    this.baseWatched,
    required this.state,
    required this.attempts,
    this.nextAttemptAt,
    this.lastErrorCode,
    this.statusCode,
    required this.createdAt,
    required this.updatedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['operation_id'] = Variable<String>(operationId);
    map['account_id'] = Variable<String>(accountId);
    map['subject_id'] = Variable<String>(subjectId);
    if (!nullToAbsent || episodeId != null) {
      map['episode_id'] = Variable<String>(episodeId);
    }
    map['kind'] = Variable<String>(kind);
    if (!nullToAbsent || collectionStatus != null) {
      map['collection_status'] = Variable<int>(collectionStatus);
    }
    if (!nullToAbsent || watched != null) {
      map['watched'] = Variable<bool>(watched);
    }
    if (!nullToAbsent || baseRemoteRevision != null) {
      map['base_remote_revision'] = Variable<String>(baseRemoteRevision);
    }
    if (!nullToAbsent || baseCollectionStatus != null) {
      map['base_collection_status'] = Variable<int>(baseCollectionStatus);
    }
    if (!nullToAbsent || baseWatched != null) {
      map['base_watched'] = Variable<bool>(baseWatched);
    }
    map['state'] = Variable<String>(state);
    map['attempts'] = Variable<int>(attempts);
    if (!nullToAbsent || nextAttemptAt != null) {
      map['next_attempt_at'] = Variable<DateTime>(nextAttemptAt);
    }
    if (!nullToAbsent || lastErrorCode != null) {
      map['last_error_code'] = Variable<String>(lastErrorCode);
    }
    if (!nullToAbsent || statusCode != null) {
      map['status_code'] = Variable<int>(statusCode);
    }
    map['created_at'] = Variable<DateTime>(createdAt);
    map['updated_at'] = Variable<DateTime>(updatedAt);
    return map;
  }

  BangumiSyncOperationsCompanion toCompanion(bool nullToAbsent) {
    return BangumiSyncOperationsCompanion(
      operationId: Value(operationId),
      accountId: Value(accountId),
      subjectId: Value(subjectId),
      episodeId: episodeId == null && nullToAbsent
          ? const Value.absent()
          : Value(episodeId),
      kind: Value(kind),
      collectionStatus: collectionStatus == null && nullToAbsent
          ? const Value.absent()
          : Value(collectionStatus),
      watched: watched == null && nullToAbsent
          ? const Value.absent()
          : Value(watched),
      baseRemoteRevision: baseRemoteRevision == null && nullToAbsent
          ? const Value.absent()
          : Value(baseRemoteRevision),
      baseCollectionStatus: baseCollectionStatus == null && nullToAbsent
          ? const Value.absent()
          : Value(baseCollectionStatus),
      baseWatched: baseWatched == null && nullToAbsent
          ? const Value.absent()
          : Value(baseWatched),
      state: Value(state),
      attempts: Value(attempts),
      nextAttemptAt: nextAttemptAt == null && nullToAbsent
          ? const Value.absent()
          : Value(nextAttemptAt),
      lastErrorCode: lastErrorCode == null && nullToAbsent
          ? const Value.absent()
          : Value(lastErrorCode),
      statusCode: statusCode == null && nullToAbsent
          ? const Value.absent()
          : Value(statusCode),
      createdAt: Value(createdAt),
      updatedAt: Value(updatedAt),
    );
  }

  factory BangumiSyncOperationRecord.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return BangumiSyncOperationRecord(
      operationId: serializer.fromJson<String>(json['operationId']),
      accountId: serializer.fromJson<String>(json['accountId']),
      subjectId: serializer.fromJson<String>(json['subjectId']),
      episodeId: serializer.fromJson<String?>(json['episodeId']),
      kind: serializer.fromJson<String>(json['kind']),
      collectionStatus: serializer.fromJson<int?>(json['collectionStatus']),
      watched: serializer.fromJson<bool?>(json['watched']),
      baseRemoteRevision: serializer.fromJson<String?>(
        json['baseRemoteRevision'],
      ),
      baseCollectionStatus: serializer.fromJson<int?>(
        json['baseCollectionStatus'],
      ),
      baseWatched: serializer.fromJson<bool?>(json['baseWatched']),
      state: serializer.fromJson<String>(json['state']),
      attempts: serializer.fromJson<int>(json['attempts']),
      nextAttemptAt: serializer.fromJson<DateTime?>(json['nextAttemptAt']),
      lastErrorCode: serializer.fromJson<String?>(json['lastErrorCode']),
      statusCode: serializer.fromJson<int?>(json['statusCode']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
      updatedAt: serializer.fromJson<DateTime>(json['updatedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'operationId': serializer.toJson<String>(operationId),
      'accountId': serializer.toJson<String>(accountId),
      'subjectId': serializer.toJson<String>(subjectId),
      'episodeId': serializer.toJson<String?>(episodeId),
      'kind': serializer.toJson<String>(kind),
      'collectionStatus': serializer.toJson<int?>(collectionStatus),
      'watched': serializer.toJson<bool?>(watched),
      'baseRemoteRevision': serializer.toJson<String?>(baseRemoteRevision),
      'baseCollectionStatus': serializer.toJson<int?>(baseCollectionStatus),
      'baseWatched': serializer.toJson<bool?>(baseWatched),
      'state': serializer.toJson<String>(state),
      'attempts': serializer.toJson<int>(attempts),
      'nextAttemptAt': serializer.toJson<DateTime?>(nextAttemptAt),
      'lastErrorCode': serializer.toJson<String?>(lastErrorCode),
      'statusCode': serializer.toJson<int?>(statusCode),
      'createdAt': serializer.toJson<DateTime>(createdAt),
      'updatedAt': serializer.toJson<DateTime>(updatedAt),
    };
  }

  BangumiSyncOperationRecord copyWith({
    String? operationId,
    String? accountId,
    String? subjectId,
    Value<String?> episodeId = const Value.absent(),
    String? kind,
    Value<int?> collectionStatus = const Value.absent(),
    Value<bool?> watched = const Value.absent(),
    Value<String?> baseRemoteRevision = const Value.absent(),
    Value<int?> baseCollectionStatus = const Value.absent(),
    Value<bool?> baseWatched = const Value.absent(),
    String? state,
    int? attempts,
    Value<DateTime?> nextAttemptAt = const Value.absent(),
    Value<String?> lastErrorCode = const Value.absent(),
    Value<int?> statusCode = const Value.absent(),
    DateTime? createdAt,
    DateTime? updatedAt,
  }) => BangumiSyncOperationRecord(
    operationId: operationId ?? this.operationId,
    accountId: accountId ?? this.accountId,
    subjectId: subjectId ?? this.subjectId,
    episodeId: episodeId.present ? episodeId.value : this.episodeId,
    kind: kind ?? this.kind,
    collectionStatus: collectionStatus.present
        ? collectionStatus.value
        : this.collectionStatus,
    watched: watched.present ? watched.value : this.watched,
    baseRemoteRevision: baseRemoteRevision.present
        ? baseRemoteRevision.value
        : this.baseRemoteRevision,
    baseCollectionStatus: baseCollectionStatus.present
        ? baseCollectionStatus.value
        : this.baseCollectionStatus,
    baseWatched: baseWatched.present ? baseWatched.value : this.baseWatched,
    state: state ?? this.state,
    attempts: attempts ?? this.attempts,
    nextAttemptAt: nextAttemptAt.present
        ? nextAttemptAt.value
        : this.nextAttemptAt,
    lastErrorCode: lastErrorCode.present
        ? lastErrorCode.value
        : this.lastErrorCode,
    statusCode: statusCode.present ? statusCode.value : this.statusCode,
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt ?? this.updatedAt,
  );
  BangumiSyncOperationRecord copyWithCompanion(
    BangumiSyncOperationsCompanion data,
  ) {
    return BangumiSyncOperationRecord(
      operationId: data.operationId.present
          ? data.operationId.value
          : this.operationId,
      accountId: data.accountId.present ? data.accountId.value : this.accountId,
      subjectId: data.subjectId.present ? data.subjectId.value : this.subjectId,
      episodeId: data.episodeId.present ? data.episodeId.value : this.episodeId,
      kind: data.kind.present ? data.kind.value : this.kind,
      collectionStatus: data.collectionStatus.present
          ? data.collectionStatus.value
          : this.collectionStatus,
      watched: data.watched.present ? data.watched.value : this.watched,
      baseRemoteRevision: data.baseRemoteRevision.present
          ? data.baseRemoteRevision.value
          : this.baseRemoteRevision,
      baseCollectionStatus: data.baseCollectionStatus.present
          ? data.baseCollectionStatus.value
          : this.baseCollectionStatus,
      baseWatched: data.baseWatched.present
          ? data.baseWatched.value
          : this.baseWatched,
      state: data.state.present ? data.state.value : this.state,
      attempts: data.attempts.present ? data.attempts.value : this.attempts,
      nextAttemptAt: data.nextAttemptAt.present
          ? data.nextAttemptAt.value
          : this.nextAttemptAt,
      lastErrorCode: data.lastErrorCode.present
          ? data.lastErrorCode.value
          : this.lastErrorCode,
      statusCode: data.statusCode.present
          ? data.statusCode.value
          : this.statusCode,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('BangumiSyncOperationRecord(')
          ..write('operationId: $operationId, ')
          ..write('accountId: $accountId, ')
          ..write('subjectId: $subjectId, ')
          ..write('episodeId: $episodeId, ')
          ..write('kind: $kind, ')
          ..write('collectionStatus: $collectionStatus, ')
          ..write('watched: $watched, ')
          ..write('baseRemoteRevision: $baseRemoteRevision, ')
          ..write('baseCollectionStatus: $baseCollectionStatus, ')
          ..write('baseWatched: $baseWatched, ')
          ..write('state: $state, ')
          ..write('attempts: $attempts, ')
          ..write('nextAttemptAt: $nextAttemptAt, ')
          ..write('lastErrorCode: $lastErrorCode, ')
          ..write('statusCode: $statusCode, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    operationId,
    accountId,
    subjectId,
    episodeId,
    kind,
    collectionStatus,
    watched,
    baseRemoteRevision,
    baseCollectionStatus,
    baseWatched,
    state,
    attempts,
    nextAttemptAt,
    lastErrorCode,
    statusCode,
    createdAt,
    updatedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is BangumiSyncOperationRecord &&
          other.operationId == this.operationId &&
          other.accountId == this.accountId &&
          other.subjectId == this.subjectId &&
          other.episodeId == this.episodeId &&
          other.kind == this.kind &&
          other.collectionStatus == this.collectionStatus &&
          other.watched == this.watched &&
          other.baseRemoteRevision == this.baseRemoteRevision &&
          other.baseCollectionStatus == this.baseCollectionStatus &&
          other.baseWatched == this.baseWatched &&
          other.state == this.state &&
          other.attempts == this.attempts &&
          other.nextAttemptAt == this.nextAttemptAt &&
          other.lastErrorCode == this.lastErrorCode &&
          other.statusCode == this.statusCode &&
          other.createdAt == this.createdAt &&
          other.updatedAt == this.updatedAt);
}

class BangumiSyncOperationsCompanion
    extends UpdateCompanion<BangumiSyncOperationRecord> {
  final Value<String> operationId;
  final Value<String> accountId;
  final Value<String> subjectId;
  final Value<String?> episodeId;
  final Value<String> kind;
  final Value<int?> collectionStatus;
  final Value<bool?> watched;
  final Value<String?> baseRemoteRevision;
  final Value<int?> baseCollectionStatus;
  final Value<bool?> baseWatched;
  final Value<String> state;
  final Value<int> attempts;
  final Value<DateTime?> nextAttemptAt;
  final Value<String?> lastErrorCode;
  final Value<int?> statusCode;
  final Value<DateTime> createdAt;
  final Value<DateTime> updatedAt;
  final Value<int> rowid;
  const BangumiSyncOperationsCompanion({
    this.operationId = const Value.absent(),
    this.accountId = const Value.absent(),
    this.subjectId = const Value.absent(),
    this.episodeId = const Value.absent(),
    this.kind = const Value.absent(),
    this.collectionStatus = const Value.absent(),
    this.watched = const Value.absent(),
    this.baseRemoteRevision = const Value.absent(),
    this.baseCollectionStatus = const Value.absent(),
    this.baseWatched = const Value.absent(),
    this.state = const Value.absent(),
    this.attempts = const Value.absent(),
    this.nextAttemptAt = const Value.absent(),
    this.lastErrorCode = const Value.absent(),
    this.statusCode = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  BangumiSyncOperationsCompanion.insert({
    required String operationId,
    required String accountId,
    required String subjectId,
    this.episodeId = const Value.absent(),
    required String kind,
    this.collectionStatus = const Value.absent(),
    this.watched = const Value.absent(),
    this.baseRemoteRevision = const Value.absent(),
    this.baseCollectionStatus = const Value.absent(),
    this.baseWatched = const Value.absent(),
    required String state,
    this.attempts = const Value.absent(),
    this.nextAttemptAt = const Value.absent(),
    this.lastErrorCode = const Value.absent(),
    this.statusCode = const Value.absent(),
    required DateTime createdAt,
    required DateTime updatedAt,
    this.rowid = const Value.absent(),
  }) : operationId = Value(operationId),
       accountId = Value(accountId),
       subjectId = Value(subjectId),
       kind = Value(kind),
       state = Value(state),
       createdAt = Value(createdAt),
       updatedAt = Value(updatedAt);
  static Insertable<BangumiSyncOperationRecord> custom({
    Expression<String>? operationId,
    Expression<String>? accountId,
    Expression<String>? subjectId,
    Expression<String>? episodeId,
    Expression<String>? kind,
    Expression<int>? collectionStatus,
    Expression<bool>? watched,
    Expression<String>? baseRemoteRevision,
    Expression<int>? baseCollectionStatus,
    Expression<bool>? baseWatched,
    Expression<String>? state,
    Expression<int>? attempts,
    Expression<DateTime>? nextAttemptAt,
    Expression<String>? lastErrorCode,
    Expression<int>? statusCode,
    Expression<DateTime>? createdAt,
    Expression<DateTime>? updatedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (operationId != null) 'operation_id': operationId,
      if (accountId != null) 'account_id': accountId,
      if (subjectId != null) 'subject_id': subjectId,
      if (episodeId != null) 'episode_id': episodeId,
      if (kind != null) 'kind': kind,
      if (collectionStatus != null) 'collection_status': collectionStatus,
      if (watched != null) 'watched': watched,
      if (baseRemoteRevision != null)
        'base_remote_revision': baseRemoteRevision,
      if (baseCollectionStatus != null)
        'base_collection_status': baseCollectionStatus,
      if (baseWatched != null) 'base_watched': baseWatched,
      if (state != null) 'state': state,
      if (attempts != null) 'attempts': attempts,
      if (nextAttemptAt != null) 'next_attempt_at': nextAttemptAt,
      if (lastErrorCode != null) 'last_error_code': lastErrorCode,
      if (statusCode != null) 'status_code': statusCode,
      if (createdAt != null) 'created_at': createdAt,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  BangumiSyncOperationsCompanion copyWith({
    Value<String>? operationId,
    Value<String>? accountId,
    Value<String>? subjectId,
    Value<String?>? episodeId,
    Value<String>? kind,
    Value<int?>? collectionStatus,
    Value<bool?>? watched,
    Value<String?>? baseRemoteRevision,
    Value<int?>? baseCollectionStatus,
    Value<bool?>? baseWatched,
    Value<String>? state,
    Value<int>? attempts,
    Value<DateTime?>? nextAttemptAt,
    Value<String?>? lastErrorCode,
    Value<int?>? statusCode,
    Value<DateTime>? createdAt,
    Value<DateTime>? updatedAt,
    Value<int>? rowid,
  }) {
    return BangumiSyncOperationsCompanion(
      operationId: operationId ?? this.operationId,
      accountId: accountId ?? this.accountId,
      subjectId: subjectId ?? this.subjectId,
      episodeId: episodeId ?? this.episodeId,
      kind: kind ?? this.kind,
      collectionStatus: collectionStatus ?? this.collectionStatus,
      watched: watched ?? this.watched,
      baseRemoteRevision: baseRemoteRevision ?? this.baseRemoteRevision,
      baseCollectionStatus: baseCollectionStatus ?? this.baseCollectionStatus,
      baseWatched: baseWatched ?? this.baseWatched,
      state: state ?? this.state,
      attempts: attempts ?? this.attempts,
      nextAttemptAt: nextAttemptAt ?? this.nextAttemptAt,
      lastErrorCode: lastErrorCode ?? this.lastErrorCode,
      statusCode: statusCode ?? this.statusCode,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (operationId.present) {
      map['operation_id'] = Variable<String>(operationId.value);
    }
    if (accountId.present) {
      map['account_id'] = Variable<String>(accountId.value);
    }
    if (subjectId.present) {
      map['subject_id'] = Variable<String>(subjectId.value);
    }
    if (episodeId.present) {
      map['episode_id'] = Variable<String>(episodeId.value);
    }
    if (kind.present) {
      map['kind'] = Variable<String>(kind.value);
    }
    if (collectionStatus.present) {
      map['collection_status'] = Variable<int>(collectionStatus.value);
    }
    if (watched.present) {
      map['watched'] = Variable<bool>(watched.value);
    }
    if (baseRemoteRevision.present) {
      map['base_remote_revision'] = Variable<String>(baseRemoteRevision.value);
    }
    if (baseCollectionStatus.present) {
      map['base_collection_status'] = Variable<int>(baseCollectionStatus.value);
    }
    if (baseWatched.present) {
      map['base_watched'] = Variable<bool>(baseWatched.value);
    }
    if (state.present) {
      map['state'] = Variable<String>(state.value);
    }
    if (attempts.present) {
      map['attempts'] = Variable<int>(attempts.value);
    }
    if (nextAttemptAt.present) {
      map['next_attempt_at'] = Variable<DateTime>(nextAttemptAt.value);
    }
    if (lastErrorCode.present) {
      map['last_error_code'] = Variable<String>(lastErrorCode.value);
    }
    if (statusCode.present) {
      map['status_code'] = Variable<int>(statusCode.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('BangumiSyncOperationsCompanion(')
          ..write('operationId: $operationId, ')
          ..write('accountId: $accountId, ')
          ..write('subjectId: $subjectId, ')
          ..write('episodeId: $episodeId, ')
          ..write('kind: $kind, ')
          ..write('collectionStatus: $collectionStatus, ')
          ..write('watched: $watched, ')
          ..write('baseRemoteRevision: $baseRemoteRevision, ')
          ..write('baseCollectionStatus: $baseCollectionStatus, ')
          ..write('baseWatched: $baseWatched, ')
          ..write('state: $state, ')
          ..write('attempts: $attempts, ')
          ..write('nextAttemptAt: $nextAttemptAt, ')
          ..write('lastErrorCode: $lastErrorCode, ')
          ..write('statusCode: $statusCode, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $BangumiConflictSnapshotsTable extends BangumiConflictSnapshots
    with
        TableInfo<
          $BangumiConflictSnapshotsTable,
          BangumiConflictSnapshotRecord
        > {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $BangumiConflictSnapshotsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _operationIdMeta = const VerificationMeta(
    'operationId',
  );
  @override
  late final GeneratedColumn<String> operationId = GeneratedColumn<String>(
    'operation_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _accountIdMeta = const VerificationMeta(
    'accountId',
  );
  @override
  late final GeneratedColumn<String> accountId = GeneratedColumn<String>(
    'account_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES bangumi_accounts (account_id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _subjectIdMeta = const VerificationMeta(
    'subjectId',
  );
  @override
  late final GeneratedColumn<String> subjectId = GeneratedColumn<String>(
    'subject_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES bangumi_subjects (subject_id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _statusMeta = const VerificationMeta('status');
  @override
  late final GeneratedColumn<int> status = GeneratedColumn<int>(
    'status',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _watchedEpisodeIdsMeta = const VerificationMeta(
    'watchedEpisodeIds',
  );
  @override
  late final GeneratedColumn<String> watchedEpisodeIds =
      GeneratedColumn<String>(
        'watched_episode_ids',
        aliasedName,
        false,
        type: DriftSqlType.string,
        requiredDuringInsert: true,
      );
  static const VerificationMeta _remoteRevisionMeta = const VerificationMeta(
    'remoteRevision',
  );
  @override
  late final GeneratedColumn<String> remoteRevision = GeneratedColumn<String>(
    'remote_revision',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _capturedAtMeta = const VerificationMeta(
    'capturedAt',
  );
  @override
  late final GeneratedColumn<DateTime> capturedAt = GeneratedColumn<DateTime>(
    'captured_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [
    operationId,
    accountId,
    subjectId,
    status,
    watchedEpisodeIds,
    remoteRevision,
    capturedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'bangumi_conflict_snapshots';
  @override
  VerificationContext validateIntegrity(
    Insertable<BangumiConflictSnapshotRecord> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('operation_id')) {
      context.handle(
        _operationIdMeta,
        operationId.isAcceptableOrUnknown(
          data['operation_id']!,
          _operationIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_operationIdMeta);
    }
    if (data.containsKey('account_id')) {
      context.handle(
        _accountIdMeta,
        accountId.isAcceptableOrUnknown(data['account_id']!, _accountIdMeta),
      );
    } else if (isInserting) {
      context.missing(_accountIdMeta);
    }
    if (data.containsKey('subject_id')) {
      context.handle(
        _subjectIdMeta,
        subjectId.isAcceptableOrUnknown(data['subject_id']!, _subjectIdMeta),
      );
    } else if (isInserting) {
      context.missing(_subjectIdMeta);
    }
    if (data.containsKey('status')) {
      context.handle(
        _statusMeta,
        status.isAcceptableOrUnknown(data['status']!, _statusMeta),
      );
    }
    if (data.containsKey('watched_episode_ids')) {
      context.handle(
        _watchedEpisodeIdsMeta,
        watchedEpisodeIds.isAcceptableOrUnknown(
          data['watched_episode_ids']!,
          _watchedEpisodeIdsMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_watchedEpisodeIdsMeta);
    }
    if (data.containsKey('remote_revision')) {
      context.handle(
        _remoteRevisionMeta,
        remoteRevision.isAcceptableOrUnknown(
          data['remote_revision']!,
          _remoteRevisionMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_remoteRevisionMeta);
    }
    if (data.containsKey('captured_at')) {
      context.handle(
        _capturedAtMeta,
        capturedAt.isAcceptableOrUnknown(data['captured_at']!, _capturedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_capturedAtMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {operationId};
  @override
  BangumiConflictSnapshotRecord map(
    Map<String, dynamic> data, {
    String? tablePrefix,
  }) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return BangumiConflictSnapshotRecord(
      operationId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}operation_id'],
      )!,
      accountId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}account_id'],
      )!,
      subjectId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}subject_id'],
      )!,
      status: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}status'],
      ),
      watchedEpisodeIds: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}watched_episode_ids'],
      )!,
      remoteRevision: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}remote_revision'],
      )!,
      capturedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}captured_at'],
      )!,
    );
  }

  @override
  $BangumiConflictSnapshotsTable createAlias(String alias) {
    return $BangumiConflictSnapshotsTable(attachedDatabase, alias);
  }
}

class BangumiConflictSnapshotRecord extends DataClass
    implements Insertable<BangumiConflictSnapshotRecord> {
  final String operationId;
  final String accountId;
  final String subjectId;
  final int? status;
  final String watchedEpisodeIds;
  final String remoteRevision;
  final DateTime capturedAt;
  const BangumiConflictSnapshotRecord({
    required this.operationId,
    required this.accountId,
    required this.subjectId,
    this.status,
    required this.watchedEpisodeIds,
    required this.remoteRevision,
    required this.capturedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['operation_id'] = Variable<String>(operationId);
    map['account_id'] = Variable<String>(accountId);
    map['subject_id'] = Variable<String>(subjectId);
    if (!nullToAbsent || status != null) {
      map['status'] = Variable<int>(status);
    }
    map['watched_episode_ids'] = Variable<String>(watchedEpisodeIds);
    map['remote_revision'] = Variable<String>(remoteRevision);
    map['captured_at'] = Variable<DateTime>(capturedAt);
    return map;
  }

  BangumiConflictSnapshotsCompanion toCompanion(bool nullToAbsent) {
    return BangumiConflictSnapshotsCompanion(
      operationId: Value(operationId),
      accountId: Value(accountId),
      subjectId: Value(subjectId),
      status: status == null && nullToAbsent
          ? const Value.absent()
          : Value(status),
      watchedEpisodeIds: Value(watchedEpisodeIds),
      remoteRevision: Value(remoteRevision),
      capturedAt: Value(capturedAt),
    );
  }

  factory BangumiConflictSnapshotRecord.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return BangumiConflictSnapshotRecord(
      operationId: serializer.fromJson<String>(json['operationId']),
      accountId: serializer.fromJson<String>(json['accountId']),
      subjectId: serializer.fromJson<String>(json['subjectId']),
      status: serializer.fromJson<int?>(json['status']),
      watchedEpisodeIds: serializer.fromJson<String>(json['watchedEpisodeIds']),
      remoteRevision: serializer.fromJson<String>(json['remoteRevision']),
      capturedAt: serializer.fromJson<DateTime>(json['capturedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'operationId': serializer.toJson<String>(operationId),
      'accountId': serializer.toJson<String>(accountId),
      'subjectId': serializer.toJson<String>(subjectId),
      'status': serializer.toJson<int?>(status),
      'watchedEpisodeIds': serializer.toJson<String>(watchedEpisodeIds),
      'remoteRevision': serializer.toJson<String>(remoteRevision),
      'capturedAt': serializer.toJson<DateTime>(capturedAt),
    };
  }

  BangumiConflictSnapshotRecord copyWith({
    String? operationId,
    String? accountId,
    String? subjectId,
    Value<int?> status = const Value.absent(),
    String? watchedEpisodeIds,
    String? remoteRevision,
    DateTime? capturedAt,
  }) => BangumiConflictSnapshotRecord(
    operationId: operationId ?? this.operationId,
    accountId: accountId ?? this.accountId,
    subjectId: subjectId ?? this.subjectId,
    status: status.present ? status.value : this.status,
    watchedEpisodeIds: watchedEpisodeIds ?? this.watchedEpisodeIds,
    remoteRevision: remoteRevision ?? this.remoteRevision,
    capturedAt: capturedAt ?? this.capturedAt,
  );
  BangumiConflictSnapshotRecord copyWithCompanion(
    BangumiConflictSnapshotsCompanion data,
  ) {
    return BangumiConflictSnapshotRecord(
      operationId: data.operationId.present
          ? data.operationId.value
          : this.operationId,
      accountId: data.accountId.present ? data.accountId.value : this.accountId,
      subjectId: data.subjectId.present ? data.subjectId.value : this.subjectId,
      status: data.status.present ? data.status.value : this.status,
      watchedEpisodeIds: data.watchedEpisodeIds.present
          ? data.watchedEpisodeIds.value
          : this.watchedEpisodeIds,
      remoteRevision: data.remoteRevision.present
          ? data.remoteRevision.value
          : this.remoteRevision,
      capturedAt: data.capturedAt.present
          ? data.capturedAt.value
          : this.capturedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('BangumiConflictSnapshotRecord(')
          ..write('operationId: $operationId, ')
          ..write('accountId: $accountId, ')
          ..write('subjectId: $subjectId, ')
          ..write('status: $status, ')
          ..write('watchedEpisodeIds: $watchedEpisodeIds, ')
          ..write('remoteRevision: $remoteRevision, ')
          ..write('capturedAt: $capturedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    operationId,
    accountId,
    subjectId,
    status,
    watchedEpisodeIds,
    remoteRevision,
    capturedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is BangumiConflictSnapshotRecord &&
          other.operationId == this.operationId &&
          other.accountId == this.accountId &&
          other.subjectId == this.subjectId &&
          other.status == this.status &&
          other.watchedEpisodeIds == this.watchedEpisodeIds &&
          other.remoteRevision == this.remoteRevision &&
          other.capturedAt == this.capturedAt);
}

class BangumiConflictSnapshotsCompanion
    extends UpdateCompanion<BangumiConflictSnapshotRecord> {
  final Value<String> operationId;
  final Value<String> accountId;
  final Value<String> subjectId;
  final Value<int?> status;
  final Value<String> watchedEpisodeIds;
  final Value<String> remoteRevision;
  final Value<DateTime> capturedAt;
  final Value<int> rowid;
  const BangumiConflictSnapshotsCompanion({
    this.operationId = const Value.absent(),
    this.accountId = const Value.absent(),
    this.subjectId = const Value.absent(),
    this.status = const Value.absent(),
    this.watchedEpisodeIds = const Value.absent(),
    this.remoteRevision = const Value.absent(),
    this.capturedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  BangumiConflictSnapshotsCompanion.insert({
    required String operationId,
    required String accountId,
    required String subjectId,
    this.status = const Value.absent(),
    required String watchedEpisodeIds,
    required String remoteRevision,
    required DateTime capturedAt,
    this.rowid = const Value.absent(),
  }) : operationId = Value(operationId),
       accountId = Value(accountId),
       subjectId = Value(subjectId),
       watchedEpisodeIds = Value(watchedEpisodeIds),
       remoteRevision = Value(remoteRevision),
       capturedAt = Value(capturedAt);
  static Insertable<BangumiConflictSnapshotRecord> custom({
    Expression<String>? operationId,
    Expression<String>? accountId,
    Expression<String>? subjectId,
    Expression<int>? status,
    Expression<String>? watchedEpisodeIds,
    Expression<String>? remoteRevision,
    Expression<DateTime>? capturedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (operationId != null) 'operation_id': operationId,
      if (accountId != null) 'account_id': accountId,
      if (subjectId != null) 'subject_id': subjectId,
      if (status != null) 'status': status,
      if (watchedEpisodeIds != null) 'watched_episode_ids': watchedEpisodeIds,
      if (remoteRevision != null) 'remote_revision': remoteRevision,
      if (capturedAt != null) 'captured_at': capturedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  BangumiConflictSnapshotsCompanion copyWith({
    Value<String>? operationId,
    Value<String>? accountId,
    Value<String>? subjectId,
    Value<int?>? status,
    Value<String>? watchedEpisodeIds,
    Value<String>? remoteRevision,
    Value<DateTime>? capturedAt,
    Value<int>? rowid,
  }) {
    return BangumiConflictSnapshotsCompanion(
      operationId: operationId ?? this.operationId,
      accountId: accountId ?? this.accountId,
      subjectId: subjectId ?? this.subjectId,
      status: status ?? this.status,
      watchedEpisodeIds: watchedEpisodeIds ?? this.watchedEpisodeIds,
      remoteRevision: remoteRevision ?? this.remoteRevision,
      capturedAt: capturedAt ?? this.capturedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (operationId.present) {
      map['operation_id'] = Variable<String>(operationId.value);
    }
    if (accountId.present) {
      map['account_id'] = Variable<String>(accountId.value);
    }
    if (subjectId.present) {
      map['subject_id'] = Variable<String>(subjectId.value);
    }
    if (status.present) {
      map['status'] = Variable<int>(status.value);
    }
    if (watchedEpisodeIds.present) {
      map['watched_episode_ids'] = Variable<String>(watchedEpisodeIds.value);
    }
    if (remoteRevision.present) {
      map['remote_revision'] = Variable<String>(remoteRevision.value);
    }
    if (capturedAt.present) {
      map['captured_at'] = Variable<DateTime>(capturedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('BangumiConflictSnapshotsCompanion(')
          ..write('operationId: $operationId, ')
          ..write('accountId: $accountId, ')
          ..write('subjectId: $subjectId, ')
          ..write('status: $status, ')
          ..write('watchedEpisodeIds: $watchedEpisodeIds, ')
          ..write('remoteRevision: $remoteRevision, ')
          ..write('capturedAt: $capturedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

abstract class _$WynimeDatabase extends GeneratedDatabase {
  _$WynimeDatabase(QueryExecutor e) : super(e);
  $WynimeDatabaseManager get managers => $WynimeDatabaseManager(this);
  late final $AppSettingsRowsTable appSettingsRows = $AppSettingsRowsTable(
    this,
  );
  late final $WatchHistoryRowsTable watchHistoryRows = $WatchHistoryRowsTable(
    this,
  );
  late final $ArtifactManifestsTable artifactManifests =
      $ArtifactManifestsTable(this);
  late final $ArtifactRowsTable artifactRows = $ArtifactRowsTable(this);
  late final $DeleteJobRowsTable deleteJobRows = $DeleteJobRowsTable(this);
  late final $BangumiAccountsTable bangumiAccounts = $BangumiAccountsTable(
    this,
  );
  late final $BangumiSchedulesTable bangumiSchedules = $BangumiSchedulesTable(
    this,
  );
  late final $BangumiSubjectsTable bangumiSubjects = $BangumiSubjectsTable(
    this,
  );
  late final $BangumiCollectionsTable bangumiCollections =
      $BangumiCollectionsTable(this);
  late final $BangumiEpisodesTable bangumiEpisodes = $BangumiEpisodesTable(
    this,
  );
  late final $BangumiEpisodeCollectionsTable bangumiEpisodeCollections =
      $BangumiEpisodeCollectionsTable(this);
  late final $BangumiMappingsTable bangumiMappings = $BangumiMappingsTable(
    this,
  );
  late final $BangumiSyncOperationsTable bangumiSyncOperations =
      $BangumiSyncOperationsTable(this);
  late final $BangumiConflictSnapshotsTable bangumiConflictSnapshots =
      $BangumiConflictSnapshotsTable(this);
  @override
  Iterable<TableInfo<Table, Object?>> get allTables =>
      allSchemaEntities.whereType<TableInfo<Table, Object?>>();
  @override
  List<DatabaseSchemaEntity> get allSchemaEntities => [
    appSettingsRows,
    watchHistoryRows,
    artifactManifests,
    artifactRows,
    deleteJobRows,
    bangumiAccounts,
    bangumiSchedules,
    bangumiSubjects,
    bangumiCollections,
    bangumiEpisodes,
    bangumiEpisodeCollections,
    bangumiMappings,
    bangumiSyncOperations,
    bangumiConflictSnapshots,
  ];
  @override
  StreamQueryUpdateRules get streamUpdateRules => const StreamQueryUpdateRules([
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'artifact_manifests',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('artifact_rows', kind: UpdateKind.delete)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'bangumi_accounts',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('bangumi_schedules', kind: UpdateKind.delete)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'bangumi_accounts',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('bangumi_collections', kind: UpdateKind.delete)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'bangumi_subjects',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('bangumi_collections', kind: UpdateKind.delete)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'bangumi_subjects',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('bangumi_episodes', kind: UpdateKind.delete)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'bangumi_accounts',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [
        TableUpdate('bangumi_episode_collections', kind: UpdateKind.delete),
      ],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'bangumi_episodes',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [
        TableUpdate('bangumi_episode_collections', kind: UpdateKind.delete),
      ],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'bangumi_accounts',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('bangumi_sync_operations', kind: UpdateKind.delete)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'bangumi_subjects',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('bangumi_sync_operations', kind: UpdateKind.delete)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'bangumi_episodes',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('bangumi_sync_operations', kind: UpdateKind.delete)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'bangumi_accounts',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [
        TableUpdate('bangumi_conflict_snapshots', kind: UpdateKind.delete),
      ],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'bangumi_subjects',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [
        TableUpdate('bangumi_conflict_snapshots', kind: UpdateKind.delete),
      ],
    ),
  ]);
}

typedef $$AppSettingsRowsTableCreateCompanionBuilder =
    AppSettingsRowsCompanion Function({
      Value<int> singletonId,
      required String theme,
      required String interfaceLanguage,
      Value<bool> telemetryEnabled,
      required DateTime updatedAt,
    });
typedef $$AppSettingsRowsTableUpdateCompanionBuilder =
    AppSettingsRowsCompanion Function({
      Value<int> singletonId,
      Value<String> theme,
      Value<String> interfaceLanguage,
      Value<bool> telemetryEnabled,
      Value<DateTime> updatedAt,
    });

class $$AppSettingsRowsTableFilterComposer
    extends Composer<_$WynimeDatabase, $AppSettingsRowsTable> {
  $$AppSettingsRowsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get singletonId => $composableBuilder(
    column: $table.singletonId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get theme => $composableBuilder(
    column: $table.theme,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get interfaceLanguage => $composableBuilder(
    column: $table.interfaceLanguage,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get telemetryEnabled => $composableBuilder(
    column: $table.telemetryEnabled,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $$AppSettingsRowsTableOrderingComposer
    extends Composer<_$WynimeDatabase, $AppSettingsRowsTable> {
  $$AppSettingsRowsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get singletonId => $composableBuilder(
    column: $table.singletonId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get theme => $composableBuilder(
    column: $table.theme,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get interfaceLanguage => $composableBuilder(
    column: $table.interfaceLanguage,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get telemetryEnabled => $composableBuilder(
    column: $table.telemetryEnabled,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$AppSettingsRowsTableAnnotationComposer
    extends Composer<_$WynimeDatabase, $AppSettingsRowsTable> {
  $$AppSettingsRowsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get singletonId => $composableBuilder(
    column: $table.singletonId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get theme =>
      $composableBuilder(column: $table.theme, builder: (column) => column);

  GeneratedColumn<String> get interfaceLanguage => $composableBuilder(
    column: $table.interfaceLanguage,
    builder: (column) => column,
  );

  GeneratedColumn<bool> get telemetryEnabled => $composableBuilder(
    column: $table.telemetryEnabled,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);
}

class $$AppSettingsRowsTableTableManager
    extends
        RootTableManager<
          _$WynimeDatabase,
          $AppSettingsRowsTable,
          SettingsRecord,
          $$AppSettingsRowsTableFilterComposer,
          $$AppSettingsRowsTableOrderingComposer,
          $$AppSettingsRowsTableAnnotationComposer,
          $$AppSettingsRowsTableCreateCompanionBuilder,
          $$AppSettingsRowsTableUpdateCompanionBuilder,
          (
            SettingsRecord,
            BaseReferences<
              _$WynimeDatabase,
              $AppSettingsRowsTable,
              SettingsRecord
            >,
          ),
          SettingsRecord,
          PrefetchHooks Function()
        > {
  $$AppSettingsRowsTableTableManager(
    _$WynimeDatabase db,
    $AppSettingsRowsTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$AppSettingsRowsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$AppSettingsRowsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$AppSettingsRowsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> singletonId = const Value.absent(),
                Value<String> theme = const Value.absent(),
                Value<String> interfaceLanguage = const Value.absent(),
                Value<bool> telemetryEnabled = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
              }) => AppSettingsRowsCompanion(
                singletonId: singletonId,
                theme: theme,
                interfaceLanguage: interfaceLanguage,
                telemetryEnabled: telemetryEnabled,
                updatedAt: updatedAt,
              ),
          createCompanionCallback:
              ({
                Value<int> singletonId = const Value.absent(),
                required String theme,
                required String interfaceLanguage,
                Value<bool> telemetryEnabled = const Value.absent(),
                required DateTime updatedAt,
              }) => AppSettingsRowsCompanion.insert(
                singletonId: singletonId,
                theme: theme,
                interfaceLanguage: interfaceLanguage,
                telemetryEnabled: telemetryEnabled,
                updatedAt: updatedAt,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$AppSettingsRowsTableProcessedTableManager =
    ProcessedTableManager<
      _$WynimeDatabase,
      $AppSettingsRowsTable,
      SettingsRecord,
      $$AppSettingsRowsTableFilterComposer,
      $$AppSettingsRowsTableOrderingComposer,
      $$AppSettingsRowsTableAnnotationComposer,
      $$AppSettingsRowsTableCreateCompanionBuilder,
      $$AppSettingsRowsTableUpdateCompanionBuilder,
      (
        SettingsRecord,
        BaseReferences<_$WynimeDatabase, $AppSettingsRowsTable, SettingsRecord>,
      ),
      SettingsRecord,
      PrefetchHooks Function()
    >;
typedef $$WatchHistoryRowsTableCreateCompanionBuilder =
    WatchHistoryRowsCompanion Function({
      required String progressId,
      required String sourceId,
      required String lineId,
      required String subjectId,
      required String episodeId,
      required int positionMs,
      required int durationMs,
      Value<bool> isCompleted,
      Value<String?> playerBackendId,
      Value<String?> timelineMapId,
      required DateTime updatedAt,
      Value<int> rowid,
    });
typedef $$WatchHistoryRowsTableUpdateCompanionBuilder =
    WatchHistoryRowsCompanion Function({
      Value<String> progressId,
      Value<String> sourceId,
      Value<String> lineId,
      Value<String> subjectId,
      Value<String> episodeId,
      Value<int> positionMs,
      Value<int> durationMs,
      Value<bool> isCompleted,
      Value<String?> playerBackendId,
      Value<String?> timelineMapId,
      Value<DateTime> updatedAt,
      Value<int> rowid,
    });

class $$WatchHistoryRowsTableFilterComposer
    extends Composer<_$WynimeDatabase, $WatchHistoryRowsTable> {
  $$WatchHistoryRowsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get progressId => $composableBuilder(
    column: $table.progressId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get sourceId => $composableBuilder(
    column: $table.sourceId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get lineId => $composableBuilder(
    column: $table.lineId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get subjectId => $composableBuilder(
    column: $table.subjectId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get episodeId => $composableBuilder(
    column: $table.episodeId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get positionMs => $composableBuilder(
    column: $table.positionMs,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get durationMs => $composableBuilder(
    column: $table.durationMs,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get isCompleted => $composableBuilder(
    column: $table.isCompleted,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get playerBackendId => $composableBuilder(
    column: $table.playerBackendId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get timelineMapId => $composableBuilder(
    column: $table.timelineMapId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $$WatchHistoryRowsTableOrderingComposer
    extends Composer<_$WynimeDatabase, $WatchHistoryRowsTable> {
  $$WatchHistoryRowsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get progressId => $composableBuilder(
    column: $table.progressId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get sourceId => $composableBuilder(
    column: $table.sourceId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get lineId => $composableBuilder(
    column: $table.lineId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get subjectId => $composableBuilder(
    column: $table.subjectId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get episodeId => $composableBuilder(
    column: $table.episodeId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get positionMs => $composableBuilder(
    column: $table.positionMs,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get durationMs => $composableBuilder(
    column: $table.durationMs,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get isCompleted => $composableBuilder(
    column: $table.isCompleted,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get playerBackendId => $composableBuilder(
    column: $table.playerBackendId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get timelineMapId => $composableBuilder(
    column: $table.timelineMapId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$WatchHistoryRowsTableAnnotationComposer
    extends Composer<_$WynimeDatabase, $WatchHistoryRowsTable> {
  $$WatchHistoryRowsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get progressId => $composableBuilder(
    column: $table.progressId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get sourceId =>
      $composableBuilder(column: $table.sourceId, builder: (column) => column);

  GeneratedColumn<String> get lineId =>
      $composableBuilder(column: $table.lineId, builder: (column) => column);

  GeneratedColumn<String> get subjectId =>
      $composableBuilder(column: $table.subjectId, builder: (column) => column);

  GeneratedColumn<String> get episodeId =>
      $composableBuilder(column: $table.episodeId, builder: (column) => column);

  GeneratedColumn<int> get positionMs => $composableBuilder(
    column: $table.positionMs,
    builder: (column) => column,
  );

  GeneratedColumn<int> get durationMs => $composableBuilder(
    column: $table.durationMs,
    builder: (column) => column,
  );

  GeneratedColumn<bool> get isCompleted => $composableBuilder(
    column: $table.isCompleted,
    builder: (column) => column,
  );

  GeneratedColumn<String> get playerBackendId => $composableBuilder(
    column: $table.playerBackendId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get timelineMapId => $composableBuilder(
    column: $table.timelineMapId,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);
}

class $$WatchHistoryRowsTableTableManager
    extends
        RootTableManager<
          _$WynimeDatabase,
          $WatchHistoryRowsTable,
          WatchHistoryRecord,
          $$WatchHistoryRowsTableFilterComposer,
          $$WatchHistoryRowsTableOrderingComposer,
          $$WatchHistoryRowsTableAnnotationComposer,
          $$WatchHistoryRowsTableCreateCompanionBuilder,
          $$WatchHistoryRowsTableUpdateCompanionBuilder,
          (
            WatchHistoryRecord,
            BaseReferences<
              _$WynimeDatabase,
              $WatchHistoryRowsTable,
              WatchHistoryRecord
            >,
          ),
          WatchHistoryRecord,
          PrefetchHooks Function()
        > {
  $$WatchHistoryRowsTableTableManager(
    _$WynimeDatabase db,
    $WatchHistoryRowsTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$WatchHistoryRowsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$WatchHistoryRowsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$WatchHistoryRowsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> progressId = const Value.absent(),
                Value<String> sourceId = const Value.absent(),
                Value<String> lineId = const Value.absent(),
                Value<String> subjectId = const Value.absent(),
                Value<String> episodeId = const Value.absent(),
                Value<int> positionMs = const Value.absent(),
                Value<int> durationMs = const Value.absent(),
                Value<bool> isCompleted = const Value.absent(),
                Value<String?> playerBackendId = const Value.absent(),
                Value<String?> timelineMapId = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => WatchHistoryRowsCompanion(
                progressId: progressId,
                sourceId: sourceId,
                lineId: lineId,
                subjectId: subjectId,
                episodeId: episodeId,
                positionMs: positionMs,
                durationMs: durationMs,
                isCompleted: isCompleted,
                playerBackendId: playerBackendId,
                timelineMapId: timelineMapId,
                updatedAt: updatedAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String progressId,
                required String sourceId,
                required String lineId,
                required String subjectId,
                required String episodeId,
                required int positionMs,
                required int durationMs,
                Value<bool> isCompleted = const Value.absent(),
                Value<String?> playerBackendId = const Value.absent(),
                Value<String?> timelineMapId = const Value.absent(),
                required DateTime updatedAt,
                Value<int> rowid = const Value.absent(),
              }) => WatchHistoryRowsCompanion.insert(
                progressId: progressId,
                sourceId: sourceId,
                lineId: lineId,
                subjectId: subjectId,
                episodeId: episodeId,
                positionMs: positionMs,
                durationMs: durationMs,
                isCompleted: isCompleted,
                playerBackendId: playerBackendId,
                timelineMapId: timelineMapId,
                updatedAt: updatedAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$WatchHistoryRowsTableProcessedTableManager =
    ProcessedTableManager<
      _$WynimeDatabase,
      $WatchHistoryRowsTable,
      WatchHistoryRecord,
      $$WatchHistoryRowsTableFilterComposer,
      $$WatchHistoryRowsTableOrderingComposer,
      $$WatchHistoryRowsTableAnnotationComposer,
      $$WatchHistoryRowsTableCreateCompanionBuilder,
      $$WatchHistoryRowsTableUpdateCompanionBuilder,
      (
        WatchHistoryRecord,
        BaseReferences<
          _$WynimeDatabase,
          $WatchHistoryRowsTable,
          WatchHistoryRecord
        >,
      ),
      WatchHistoryRecord,
      PrefetchHooks Function()
    >;
typedef $$ArtifactManifestsTableCreateCompanionBuilder =
    ArtifactManifestsCompanion Function({
      required String manifestId,
      required String downloadId,
      required DateTime createdAt,
      Value<int> rowid,
    });
typedef $$ArtifactManifestsTableUpdateCompanionBuilder =
    ArtifactManifestsCompanion Function({
      Value<String> manifestId,
      Value<String> downloadId,
      Value<DateTime> createdAt,
      Value<int> rowid,
    });

final class $$ArtifactManifestsTableReferences
    extends
        BaseReferences<
          _$WynimeDatabase,
          $ArtifactManifestsTable,
          ArtifactManifestRecord
        > {
  $$ArtifactManifestsTableReferences(
    super.$_db,
    super.$_table,
    super.$_typedResult,
  );

  static MultiTypedResultKey<$ArtifactRowsTable, List<ArtifactRecord>>
  _artifactRowsRefsTable(_$WynimeDatabase db) => MultiTypedResultKey.fromTable(
    db.artifactRows,
    aliasName: 'artifact_manifests__manifest_id__artifact_rows__manifest_id',
  );

  $$ArtifactRowsTableProcessedTableManager get artifactRowsRefs {
    final manager = $$ArtifactRowsTableTableManager($_db, $_db.artifactRows)
        .filter(
          (f) => f.manifestId.manifestId.sqlEquals(
            $_itemColumn<String>('manifest_id')!,
          ),
        );

    final cache = $_typedResult.readTableOrNull(_artifactRowsRefsTable($_db));
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }

  static MultiTypedResultKey<$DeleteJobRowsTable, List<DeleteJobRecord>>
  _deleteJobRowsRefsTable(_$WynimeDatabase db) => MultiTypedResultKey.fromTable(
    db.deleteJobRows,
    aliasName:
        'artifact_manifests__manifest_id__delete_job_rows__artifact_manifest_id',
  );

  $$DeleteJobRowsTableProcessedTableManager get deleteJobRowsRefs {
    final manager = $$DeleteJobRowsTableTableManager($_db, $_db.deleteJobRows)
        .filter(
          (f) => f.artifactManifestId.manifestId.sqlEquals(
            $_itemColumn<String>('manifest_id')!,
          ),
        );

    final cache = $_typedResult.readTableOrNull(_deleteJobRowsRefsTable($_db));
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }
}

class $$ArtifactManifestsTableFilterComposer
    extends Composer<_$WynimeDatabase, $ArtifactManifestsTable> {
  $$ArtifactManifestsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get manifestId => $composableBuilder(
    column: $table.manifestId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get downloadId => $composableBuilder(
    column: $table.downloadId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  Expression<bool> artifactRowsRefs(
    Expression<bool> Function($$ArtifactRowsTableFilterComposer f) f,
  ) {
    final $$ArtifactRowsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.manifestId,
      referencedTable: $db.artifactRows,
      getReferencedColumn: (t) => t.manifestId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$ArtifactRowsTableFilterComposer(
            $db: $db,
            $table: $db.artifactRows,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<bool> deleteJobRowsRefs(
    Expression<bool> Function($$DeleteJobRowsTableFilterComposer f) f,
  ) {
    final $$DeleteJobRowsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.manifestId,
      referencedTable: $db.deleteJobRows,
      getReferencedColumn: (t) => t.artifactManifestId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DeleteJobRowsTableFilterComposer(
            $db: $db,
            $table: $db.deleteJobRows,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }
}

class $$ArtifactManifestsTableOrderingComposer
    extends Composer<_$WynimeDatabase, $ArtifactManifestsTable> {
  $$ArtifactManifestsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get manifestId => $composableBuilder(
    column: $table.manifestId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get downloadId => $composableBuilder(
    column: $table.downloadId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$ArtifactManifestsTableAnnotationComposer
    extends Composer<_$WynimeDatabase, $ArtifactManifestsTable> {
  $$ArtifactManifestsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get manifestId => $composableBuilder(
    column: $table.manifestId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get downloadId => $composableBuilder(
    column: $table.downloadId,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  Expression<T> artifactRowsRefs<T extends Object>(
    Expression<T> Function($$ArtifactRowsTableAnnotationComposer a) f,
  ) {
    final $$ArtifactRowsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.manifestId,
      referencedTable: $db.artifactRows,
      getReferencedColumn: (t) => t.manifestId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$ArtifactRowsTableAnnotationComposer(
            $db: $db,
            $table: $db.artifactRows,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<T> deleteJobRowsRefs<T extends Object>(
    Expression<T> Function($$DeleteJobRowsTableAnnotationComposer a) f,
  ) {
    final $$DeleteJobRowsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.manifestId,
      referencedTable: $db.deleteJobRows,
      getReferencedColumn: (t) => t.artifactManifestId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DeleteJobRowsTableAnnotationComposer(
            $db: $db,
            $table: $db.deleteJobRows,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }
}

class $$ArtifactManifestsTableTableManager
    extends
        RootTableManager<
          _$WynimeDatabase,
          $ArtifactManifestsTable,
          ArtifactManifestRecord,
          $$ArtifactManifestsTableFilterComposer,
          $$ArtifactManifestsTableOrderingComposer,
          $$ArtifactManifestsTableAnnotationComposer,
          $$ArtifactManifestsTableCreateCompanionBuilder,
          $$ArtifactManifestsTableUpdateCompanionBuilder,
          (ArtifactManifestRecord, $$ArtifactManifestsTableReferences),
          ArtifactManifestRecord,
          PrefetchHooks Function({
            bool artifactRowsRefs,
            bool deleteJobRowsRefs,
          })
        > {
  $$ArtifactManifestsTableTableManager(
    _$WynimeDatabase db,
    $ArtifactManifestsTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$ArtifactManifestsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$ArtifactManifestsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$ArtifactManifestsTableAnnotationComposer(
                $db: db,
                $table: table,
              ),
          updateCompanionCallback:
              ({
                Value<String> manifestId = const Value.absent(),
                Value<String> downloadId = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => ArtifactManifestsCompanion(
                manifestId: manifestId,
                downloadId: downloadId,
                createdAt: createdAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String manifestId,
                required String downloadId,
                required DateTime createdAt,
                Value<int> rowid = const Value.absent(),
              }) => ArtifactManifestsCompanion.insert(
                manifestId: manifestId,
                downloadId: downloadId,
                createdAt: createdAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable(table),
                  $$ArtifactManifestsTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback:
              ({artifactRowsRefs = false, deleteJobRowsRefs = false}) {
                return PrefetchHooks(
                  db: db,
                  explicitlyWatchedTables: [
                    if (artifactRowsRefs) db.artifactRows,
                    if (deleteJobRowsRefs) db.deleteJobRows,
                  ],
                  addJoins: null,
                  getPrefetchedDataCallback: (items) async {
                    return [
                      if (artifactRowsRefs)
                        await $_getPrefetchedData<
                          ArtifactManifestRecord,
                          $ArtifactManifestsTable,
                          ArtifactRecord
                        >(
                          currentTable: table,
                          referencedTable: $$ArtifactManifestsTableReferences
                              ._artifactRowsRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$ArtifactManifestsTableReferences(
                                db,
                                table,
                                p0,
                              ).artifactRowsRefs,
                          referencedItemsForCurrentItem:
                              (item, referencedItems) => referencedItems.where(
                                (e) => e.manifestId == item.manifestId,
                              ),
                          typedResults: items,
                        ),
                      if (deleteJobRowsRefs)
                        await $_getPrefetchedData<
                          ArtifactManifestRecord,
                          $ArtifactManifestsTable,
                          DeleteJobRecord
                        >(
                          currentTable: table,
                          referencedTable: $$ArtifactManifestsTableReferences
                              ._deleteJobRowsRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$ArtifactManifestsTableReferences(
                                db,
                                table,
                                p0,
                              ).deleteJobRowsRefs,
                          referencedItemsForCurrentItem:
                              (item, referencedItems) => referencedItems.where(
                                (e) => e.artifactManifestId == item.manifestId,
                              ),
                          typedResults: items,
                        ),
                    ];
                  },
                );
              },
        ),
      );
}

typedef $$ArtifactManifestsTableProcessedTableManager =
    ProcessedTableManager<
      _$WynimeDatabase,
      $ArtifactManifestsTable,
      ArtifactManifestRecord,
      $$ArtifactManifestsTableFilterComposer,
      $$ArtifactManifestsTableOrderingComposer,
      $$ArtifactManifestsTableAnnotationComposer,
      $$ArtifactManifestsTableCreateCompanionBuilder,
      $$ArtifactManifestsTableUpdateCompanionBuilder,
      (ArtifactManifestRecord, $$ArtifactManifestsTableReferences),
      ArtifactManifestRecord,
      PrefetchHooks Function({bool artifactRowsRefs, bool deleteJobRowsRefs})
    >;
typedef $$ArtifactRowsTableCreateCompanionBuilder =
    ArtifactRowsCompanion Function({
      required String artifactId,
      required String manifestId,
      required String kind,
      required String fileUri,
      Value<int> rowid,
    });
typedef $$ArtifactRowsTableUpdateCompanionBuilder =
    ArtifactRowsCompanion Function({
      Value<String> artifactId,
      Value<String> manifestId,
      Value<String> kind,
      Value<String> fileUri,
      Value<int> rowid,
    });

final class $$ArtifactRowsTableReferences
    extends
        BaseReferences<_$WynimeDatabase, $ArtifactRowsTable, ArtifactRecord> {
  $$ArtifactRowsTableReferences(super.$_db, super.$_table, super.$_typedResult);

  static $ArtifactManifestsTable _manifestIdTable(_$WynimeDatabase db) =>
      db.artifactManifests.createAlias(
        'artifact_rows__manifest_id__artifact_manifests__manifest_id',
      );

  $$ArtifactManifestsTableProcessedTableManager get manifestId {
    final $_column = $_itemColumn<String>('manifest_id')!;

    final manager = $$ArtifactManifestsTableTableManager(
      $_db,
      $_db.artifactManifests,
    ).filter((f) => f.manifestId.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_manifestIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }
}

class $$ArtifactRowsTableFilterComposer
    extends Composer<_$WynimeDatabase, $ArtifactRowsTable> {
  $$ArtifactRowsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get artifactId => $composableBuilder(
    column: $table.artifactId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get fileUri => $composableBuilder(
    column: $table.fileUri,
    builder: (column) => ColumnFilters(column),
  );

  $$ArtifactManifestsTableFilterComposer get manifestId {
    final $$ArtifactManifestsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.manifestId,
      referencedTable: $db.artifactManifests,
      getReferencedColumn: (t) => t.manifestId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$ArtifactManifestsTableFilterComposer(
            $db: $db,
            $table: $db.artifactManifests,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$ArtifactRowsTableOrderingComposer
    extends Composer<_$WynimeDatabase, $ArtifactRowsTable> {
  $$ArtifactRowsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get artifactId => $composableBuilder(
    column: $table.artifactId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get fileUri => $composableBuilder(
    column: $table.fileUri,
    builder: (column) => ColumnOrderings(column),
  );

  $$ArtifactManifestsTableOrderingComposer get manifestId {
    final $$ArtifactManifestsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.manifestId,
      referencedTable: $db.artifactManifests,
      getReferencedColumn: (t) => t.manifestId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$ArtifactManifestsTableOrderingComposer(
            $db: $db,
            $table: $db.artifactManifests,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$ArtifactRowsTableAnnotationComposer
    extends Composer<_$WynimeDatabase, $ArtifactRowsTable> {
  $$ArtifactRowsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get artifactId => $composableBuilder(
    column: $table.artifactId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get kind =>
      $composableBuilder(column: $table.kind, builder: (column) => column);

  GeneratedColumn<String> get fileUri =>
      $composableBuilder(column: $table.fileUri, builder: (column) => column);

  $$ArtifactManifestsTableAnnotationComposer get manifestId {
    final $$ArtifactManifestsTableAnnotationComposer composer =
        $composerBuilder(
          composer: this,
          getCurrentColumn: (t) => t.manifestId,
          referencedTable: $db.artifactManifests,
          getReferencedColumn: (t) => t.manifestId,
          builder:
              (
                joinBuilder, {
                $addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer,
              }) => $$ArtifactManifestsTableAnnotationComposer(
                $db: $db,
                $table: $db.artifactManifests,
                $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
                joinBuilder: joinBuilder,
                $removeJoinBuilderFromRootComposer:
                    $removeJoinBuilderFromRootComposer,
              ),
        );
    return composer;
  }
}

class $$ArtifactRowsTableTableManager
    extends
        RootTableManager<
          _$WynimeDatabase,
          $ArtifactRowsTable,
          ArtifactRecord,
          $$ArtifactRowsTableFilterComposer,
          $$ArtifactRowsTableOrderingComposer,
          $$ArtifactRowsTableAnnotationComposer,
          $$ArtifactRowsTableCreateCompanionBuilder,
          $$ArtifactRowsTableUpdateCompanionBuilder,
          (ArtifactRecord, $$ArtifactRowsTableReferences),
          ArtifactRecord,
          PrefetchHooks Function({bool manifestId})
        > {
  $$ArtifactRowsTableTableManager(_$WynimeDatabase db, $ArtifactRowsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$ArtifactRowsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$ArtifactRowsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$ArtifactRowsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> artifactId = const Value.absent(),
                Value<String> manifestId = const Value.absent(),
                Value<String> kind = const Value.absent(),
                Value<String> fileUri = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => ArtifactRowsCompanion(
                artifactId: artifactId,
                manifestId: manifestId,
                kind: kind,
                fileUri: fileUri,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String artifactId,
                required String manifestId,
                required String kind,
                required String fileUri,
                Value<int> rowid = const Value.absent(),
              }) => ArtifactRowsCompanion.insert(
                artifactId: artifactId,
                manifestId: manifestId,
                kind: kind,
                fileUri: fileUri,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable(table),
                  $$ArtifactRowsTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({manifestId = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [],
              addJoins:
                  <
                    T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic
                    >
                  >(state) {
                    if (manifestId) {
                      state =
                          state.withJoin(
                                currentTable: table,
                                currentColumn: table.manifestId,
                                referencedTable: $$ArtifactRowsTableReferences
                                    ._manifestIdTable(db),
                                referencedColumn: $$ArtifactRowsTableReferences
                                    ._manifestIdTable(db)
                                    .manifestId,
                              )
                              as T;
                    }

                    return state;
                  },
              getPrefetchedDataCallback: (items) async {
                return [];
              },
            );
          },
        ),
      );
}

typedef $$ArtifactRowsTableProcessedTableManager =
    ProcessedTableManager<
      _$WynimeDatabase,
      $ArtifactRowsTable,
      ArtifactRecord,
      $$ArtifactRowsTableFilterComposer,
      $$ArtifactRowsTableOrderingComposer,
      $$ArtifactRowsTableAnnotationComposer,
      $$ArtifactRowsTableCreateCompanionBuilder,
      $$ArtifactRowsTableUpdateCompanionBuilder,
      (ArtifactRecord, $$ArtifactRowsTableReferences),
      ArtifactRecord,
      PrefetchHooks Function({bool manifestId})
    >;
typedef $$DeleteJobRowsTableCreateCompanionBuilder =
    DeleteJobRowsCompanion Function({
      required String jobId,
      required String artifactManifestId,
      required String status,
      Value<int> attempts,
      Value<String?> failureCode,
      required DateTime createdAt,
      required DateTime updatedAt,
      Value<int> rowid,
    });
typedef $$DeleteJobRowsTableUpdateCompanionBuilder =
    DeleteJobRowsCompanion Function({
      Value<String> jobId,
      Value<String> artifactManifestId,
      Value<String> status,
      Value<int> attempts,
      Value<String?> failureCode,
      Value<DateTime> createdAt,
      Value<DateTime> updatedAt,
      Value<int> rowid,
    });

final class $$DeleteJobRowsTableReferences
    extends
        BaseReferences<_$WynimeDatabase, $DeleteJobRowsTable, DeleteJobRecord> {
  $$DeleteJobRowsTableReferences(
    super.$_db,
    super.$_table,
    super.$_typedResult,
  );

  static $ArtifactManifestsTable _artifactManifestIdTable(
    _$WynimeDatabase db,
  ) => db.artifactManifests.createAlias(
    'delete_job_rows__artifact_manifest_id__artifact_manifests__manifest_id',
  );

  $$ArtifactManifestsTableProcessedTableManager get artifactManifestId {
    final $_column = $_itemColumn<String>('artifact_manifest_id')!;

    final manager = $$ArtifactManifestsTableTableManager(
      $_db,
      $_db.artifactManifests,
    ).filter((f) => f.manifestId.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_artifactManifestIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }
}

class $$DeleteJobRowsTableFilterComposer
    extends Composer<_$WynimeDatabase, $DeleteJobRowsTable> {
  $$DeleteJobRowsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get jobId => $composableBuilder(
    column: $table.jobId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get attempts => $composableBuilder(
    column: $table.attempts,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get failureCode => $composableBuilder(
    column: $table.failureCode,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnFilters(column),
  );

  $$ArtifactManifestsTableFilterComposer get artifactManifestId {
    final $$ArtifactManifestsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.artifactManifestId,
      referencedTable: $db.artifactManifests,
      getReferencedColumn: (t) => t.manifestId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$ArtifactManifestsTableFilterComposer(
            $db: $db,
            $table: $db.artifactManifests,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$DeleteJobRowsTableOrderingComposer
    extends Composer<_$WynimeDatabase, $DeleteJobRowsTable> {
  $$DeleteJobRowsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get jobId => $composableBuilder(
    column: $table.jobId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get attempts => $composableBuilder(
    column: $table.attempts,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get failureCode => $composableBuilder(
    column: $table.failureCode,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnOrderings(column),
  );

  $$ArtifactManifestsTableOrderingComposer get artifactManifestId {
    final $$ArtifactManifestsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.artifactManifestId,
      referencedTable: $db.artifactManifests,
      getReferencedColumn: (t) => t.manifestId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$ArtifactManifestsTableOrderingComposer(
            $db: $db,
            $table: $db.artifactManifests,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$DeleteJobRowsTableAnnotationComposer
    extends Composer<_$WynimeDatabase, $DeleteJobRowsTable> {
  $$DeleteJobRowsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get jobId =>
      $composableBuilder(column: $table.jobId, builder: (column) => column);

  GeneratedColumn<String> get status =>
      $composableBuilder(column: $table.status, builder: (column) => column);

  GeneratedColumn<int> get attempts =>
      $composableBuilder(column: $table.attempts, builder: (column) => column);

  GeneratedColumn<String> get failureCode => $composableBuilder(
    column: $table.failureCode,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);

  $$ArtifactManifestsTableAnnotationComposer get artifactManifestId {
    final $$ArtifactManifestsTableAnnotationComposer composer =
        $composerBuilder(
          composer: this,
          getCurrentColumn: (t) => t.artifactManifestId,
          referencedTable: $db.artifactManifests,
          getReferencedColumn: (t) => t.manifestId,
          builder:
              (
                joinBuilder, {
                $addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer,
              }) => $$ArtifactManifestsTableAnnotationComposer(
                $db: $db,
                $table: $db.artifactManifests,
                $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
                joinBuilder: joinBuilder,
                $removeJoinBuilderFromRootComposer:
                    $removeJoinBuilderFromRootComposer,
              ),
        );
    return composer;
  }
}

class $$DeleteJobRowsTableTableManager
    extends
        RootTableManager<
          _$WynimeDatabase,
          $DeleteJobRowsTable,
          DeleteJobRecord,
          $$DeleteJobRowsTableFilterComposer,
          $$DeleteJobRowsTableOrderingComposer,
          $$DeleteJobRowsTableAnnotationComposer,
          $$DeleteJobRowsTableCreateCompanionBuilder,
          $$DeleteJobRowsTableUpdateCompanionBuilder,
          (DeleteJobRecord, $$DeleteJobRowsTableReferences),
          DeleteJobRecord,
          PrefetchHooks Function({bool artifactManifestId})
        > {
  $$DeleteJobRowsTableTableManager(
    _$WynimeDatabase db,
    $DeleteJobRowsTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$DeleteJobRowsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$DeleteJobRowsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$DeleteJobRowsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> jobId = const Value.absent(),
                Value<String> artifactManifestId = const Value.absent(),
                Value<String> status = const Value.absent(),
                Value<int> attempts = const Value.absent(),
                Value<String?> failureCode = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => DeleteJobRowsCompanion(
                jobId: jobId,
                artifactManifestId: artifactManifestId,
                status: status,
                attempts: attempts,
                failureCode: failureCode,
                createdAt: createdAt,
                updatedAt: updatedAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String jobId,
                required String artifactManifestId,
                required String status,
                Value<int> attempts = const Value.absent(),
                Value<String?> failureCode = const Value.absent(),
                required DateTime createdAt,
                required DateTime updatedAt,
                Value<int> rowid = const Value.absent(),
              }) => DeleteJobRowsCompanion.insert(
                jobId: jobId,
                artifactManifestId: artifactManifestId,
                status: status,
                attempts: attempts,
                failureCode: failureCode,
                createdAt: createdAt,
                updatedAt: updatedAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable(table),
                  $$DeleteJobRowsTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({artifactManifestId = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [],
              addJoins:
                  <
                    T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic
                    >
                  >(state) {
                    if (artifactManifestId) {
                      state =
                          state.withJoin(
                                currentTable: table,
                                currentColumn: table.artifactManifestId,
                                referencedTable: $$DeleteJobRowsTableReferences
                                    ._artifactManifestIdTable(db),
                                referencedColumn: $$DeleteJobRowsTableReferences
                                    ._artifactManifestIdTable(db)
                                    .manifestId,
                              )
                              as T;
                    }

                    return state;
                  },
              getPrefetchedDataCallback: (items) async {
                return [];
              },
            );
          },
        ),
      );
}

typedef $$DeleteJobRowsTableProcessedTableManager =
    ProcessedTableManager<
      _$WynimeDatabase,
      $DeleteJobRowsTable,
      DeleteJobRecord,
      $$DeleteJobRowsTableFilterComposer,
      $$DeleteJobRowsTableOrderingComposer,
      $$DeleteJobRowsTableAnnotationComposer,
      $$DeleteJobRowsTableCreateCompanionBuilder,
      $$DeleteJobRowsTableUpdateCompanionBuilder,
      (DeleteJobRecord, $$DeleteJobRowsTableReferences),
      DeleteJobRecord,
      PrefetchHooks Function({bool artifactManifestId})
    >;
typedef $$BangumiAccountsTableCreateCompanionBuilder =
    BangumiAccountsCompanion Function({
      required String accountId,
      required String username,
      Value<String?> nickname,
      Value<String?> avatarUrl,
      Value<bool> isActive,
      Value<DateTime?> scheduleUpdatedAt,
      required DateTime lastSeenAt,
      Value<int> rowid,
    });
typedef $$BangumiAccountsTableUpdateCompanionBuilder =
    BangumiAccountsCompanion Function({
      Value<String> accountId,
      Value<String> username,
      Value<String?> nickname,
      Value<String?> avatarUrl,
      Value<bool> isActive,
      Value<DateTime?> scheduleUpdatedAt,
      Value<DateTime> lastSeenAt,
      Value<int> rowid,
    });

final class $$BangumiAccountsTableReferences
    extends
        BaseReferences<
          _$WynimeDatabase,
          $BangumiAccountsTable,
          BangumiAccountRecord
        > {
  $$BangumiAccountsTableReferences(
    super.$_db,
    super.$_table,
    super.$_typedResult,
  );

  static MultiTypedResultKey<
    $BangumiSchedulesTable,
    List<BangumiScheduleRecord>
  >
  _bangumiSchedulesRefsTable(_$WynimeDatabase db) =>
      MultiTypedResultKey.fromTable(
        db.bangumiSchedules,
        aliasName:
            'bangumi_accounts__account_id__bangumi_schedules__account_id',
      );

  $$BangumiSchedulesTableProcessedTableManager get bangumiSchedulesRefs {
    final manager =
        $$BangumiSchedulesTableTableManager($_db, $_db.bangumiSchedules).filter(
          (f) => f.accountId.accountId.sqlEquals(
            $_itemColumn<String>('account_id')!,
          ),
        );

    final cache = $_typedResult.readTableOrNull(
      _bangumiSchedulesRefsTable($_db),
    );
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }

  static MultiTypedResultKey<
    $BangumiCollectionsTable,
    List<BangumiCollectionRecord>
  >
  _bangumiCollectionsRefsTable(_$WynimeDatabase db) =>
      MultiTypedResultKey.fromTable(
        db.bangumiCollections,
        aliasName:
            'bangumi_accounts__account_id__bangumi_collections__account_id',
      );

  $$BangumiCollectionsTableProcessedTableManager get bangumiCollectionsRefs {
    final manager =
        $$BangumiCollectionsTableTableManager(
          $_db,
          $_db.bangumiCollections,
        ).filter(
          (f) => f.accountId.accountId.sqlEquals(
            $_itemColumn<String>('account_id')!,
          ),
        );

    final cache = $_typedResult.readTableOrNull(
      _bangumiCollectionsRefsTable($_db),
    );
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }

  static MultiTypedResultKey<
    $BangumiEpisodeCollectionsTable,
    List<BangumiEpisodeCollectionRecord>
  >
  _bangumiEpisodeCollectionsRefsTable(
    _$WynimeDatabase db,
  ) => MultiTypedResultKey.fromTable(
    db.bangumiEpisodeCollections,
    aliasName:
        'bangumi_accounts__account_id__bangumi_episode_collections__account_id',
  );

  $$BangumiEpisodeCollectionsTableProcessedTableManager
  get bangumiEpisodeCollectionsRefs {
    final manager =
        $$BangumiEpisodeCollectionsTableTableManager(
          $_db,
          $_db.bangumiEpisodeCollections,
        ).filter(
          (f) => f.accountId.accountId.sqlEquals(
            $_itemColumn<String>('account_id')!,
          ),
        );

    final cache = $_typedResult.readTableOrNull(
      _bangumiEpisodeCollectionsRefsTable($_db),
    );
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }

  static MultiTypedResultKey<
    $BangumiSyncOperationsTable,
    List<BangumiSyncOperationRecord>
  >
  _bangumiSyncOperationsRefsTable(_$WynimeDatabase db) =>
      MultiTypedResultKey.fromTable(
        db.bangumiSyncOperations,
        aliasName:
            'bangumi_accounts__account_id__bangumi_sync_operations__account_id',
      );

  $$BangumiSyncOperationsTableProcessedTableManager
  get bangumiSyncOperationsRefs {
    final manager =
        $$BangumiSyncOperationsTableTableManager(
          $_db,
          $_db.bangumiSyncOperations,
        ).filter(
          (f) => f.accountId.accountId.sqlEquals(
            $_itemColumn<String>('account_id')!,
          ),
        );

    final cache = $_typedResult.readTableOrNull(
      _bangumiSyncOperationsRefsTable($_db),
    );
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }

  static MultiTypedResultKey<
    $BangumiConflictSnapshotsTable,
    List<BangumiConflictSnapshotRecord>
  >
  _bangumiConflictSnapshotsRefsTable(
    _$WynimeDatabase db,
  ) => MultiTypedResultKey.fromTable(
    db.bangumiConflictSnapshots,
    aliasName:
        'bangumi_accounts__account_id__bangumi_conflict_snapshots__account_id',
  );

  $$BangumiConflictSnapshotsTableProcessedTableManager
  get bangumiConflictSnapshotsRefs {
    final manager =
        $$BangumiConflictSnapshotsTableTableManager(
          $_db,
          $_db.bangumiConflictSnapshots,
        ).filter(
          (f) => f.accountId.accountId.sqlEquals(
            $_itemColumn<String>('account_id')!,
          ),
        );

    final cache = $_typedResult.readTableOrNull(
      _bangumiConflictSnapshotsRefsTable($_db),
    );
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }
}

class $$BangumiAccountsTableFilterComposer
    extends Composer<_$WynimeDatabase, $BangumiAccountsTable> {
  $$BangumiAccountsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get accountId => $composableBuilder(
    column: $table.accountId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get username => $composableBuilder(
    column: $table.username,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get nickname => $composableBuilder(
    column: $table.nickname,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get avatarUrl => $composableBuilder(
    column: $table.avatarUrl,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get isActive => $composableBuilder(
    column: $table.isActive,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get scheduleUpdatedAt => $composableBuilder(
    column: $table.scheduleUpdatedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get lastSeenAt => $composableBuilder(
    column: $table.lastSeenAt,
    builder: (column) => ColumnFilters(column),
  );

  Expression<bool> bangumiSchedulesRefs(
    Expression<bool> Function($$BangumiSchedulesTableFilterComposer f) f,
  ) {
    final $$BangumiSchedulesTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.accountId,
      referencedTable: $db.bangumiSchedules,
      getReferencedColumn: (t) => t.accountId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiSchedulesTableFilterComposer(
            $db: $db,
            $table: $db.bangumiSchedules,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<bool> bangumiCollectionsRefs(
    Expression<bool> Function($$BangumiCollectionsTableFilterComposer f) f,
  ) {
    final $$BangumiCollectionsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.accountId,
      referencedTable: $db.bangumiCollections,
      getReferencedColumn: (t) => t.accountId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiCollectionsTableFilterComposer(
            $db: $db,
            $table: $db.bangumiCollections,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<bool> bangumiEpisodeCollectionsRefs(
    Expression<bool> Function($$BangumiEpisodeCollectionsTableFilterComposer f)
    f,
  ) {
    final $$BangumiEpisodeCollectionsTableFilterComposer composer =
        $composerBuilder(
          composer: this,
          getCurrentColumn: (t) => t.accountId,
          referencedTable: $db.bangumiEpisodeCollections,
          getReferencedColumn: (t) => t.accountId,
          builder:
              (
                joinBuilder, {
                $addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer,
              }) => $$BangumiEpisodeCollectionsTableFilterComposer(
                $db: $db,
                $table: $db.bangumiEpisodeCollections,
                $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
                joinBuilder: joinBuilder,
                $removeJoinBuilderFromRootComposer:
                    $removeJoinBuilderFromRootComposer,
              ),
        );
    return f(composer);
  }

  Expression<bool> bangumiSyncOperationsRefs(
    Expression<bool> Function($$BangumiSyncOperationsTableFilterComposer f) f,
  ) {
    final $$BangumiSyncOperationsTableFilterComposer composer =
        $composerBuilder(
          composer: this,
          getCurrentColumn: (t) => t.accountId,
          referencedTable: $db.bangumiSyncOperations,
          getReferencedColumn: (t) => t.accountId,
          builder:
              (
                joinBuilder, {
                $addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer,
              }) => $$BangumiSyncOperationsTableFilterComposer(
                $db: $db,
                $table: $db.bangumiSyncOperations,
                $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
                joinBuilder: joinBuilder,
                $removeJoinBuilderFromRootComposer:
                    $removeJoinBuilderFromRootComposer,
              ),
        );
    return f(composer);
  }

  Expression<bool> bangumiConflictSnapshotsRefs(
    Expression<bool> Function($$BangumiConflictSnapshotsTableFilterComposer f)
    f,
  ) {
    final $$BangumiConflictSnapshotsTableFilterComposer composer =
        $composerBuilder(
          composer: this,
          getCurrentColumn: (t) => t.accountId,
          referencedTable: $db.bangumiConflictSnapshots,
          getReferencedColumn: (t) => t.accountId,
          builder:
              (
                joinBuilder, {
                $addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer,
              }) => $$BangumiConflictSnapshotsTableFilterComposer(
                $db: $db,
                $table: $db.bangumiConflictSnapshots,
                $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
                joinBuilder: joinBuilder,
                $removeJoinBuilderFromRootComposer:
                    $removeJoinBuilderFromRootComposer,
              ),
        );
    return f(composer);
  }
}

class $$BangumiAccountsTableOrderingComposer
    extends Composer<_$WynimeDatabase, $BangumiAccountsTable> {
  $$BangumiAccountsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get accountId => $composableBuilder(
    column: $table.accountId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get username => $composableBuilder(
    column: $table.username,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get nickname => $composableBuilder(
    column: $table.nickname,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get avatarUrl => $composableBuilder(
    column: $table.avatarUrl,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get isActive => $composableBuilder(
    column: $table.isActive,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get scheduleUpdatedAt => $composableBuilder(
    column: $table.scheduleUpdatedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get lastSeenAt => $composableBuilder(
    column: $table.lastSeenAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$BangumiAccountsTableAnnotationComposer
    extends Composer<_$WynimeDatabase, $BangumiAccountsTable> {
  $$BangumiAccountsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get accountId =>
      $composableBuilder(column: $table.accountId, builder: (column) => column);

  GeneratedColumn<String> get username =>
      $composableBuilder(column: $table.username, builder: (column) => column);

  GeneratedColumn<String> get nickname =>
      $composableBuilder(column: $table.nickname, builder: (column) => column);

  GeneratedColumn<String> get avatarUrl =>
      $composableBuilder(column: $table.avatarUrl, builder: (column) => column);

  GeneratedColumn<bool> get isActive =>
      $composableBuilder(column: $table.isActive, builder: (column) => column);

  GeneratedColumn<DateTime> get scheduleUpdatedAt => $composableBuilder(
    column: $table.scheduleUpdatedAt,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get lastSeenAt => $composableBuilder(
    column: $table.lastSeenAt,
    builder: (column) => column,
  );

  Expression<T> bangumiSchedulesRefs<T extends Object>(
    Expression<T> Function($$BangumiSchedulesTableAnnotationComposer a) f,
  ) {
    final $$BangumiSchedulesTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.accountId,
      referencedTable: $db.bangumiSchedules,
      getReferencedColumn: (t) => t.accountId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiSchedulesTableAnnotationComposer(
            $db: $db,
            $table: $db.bangumiSchedules,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<T> bangumiCollectionsRefs<T extends Object>(
    Expression<T> Function($$BangumiCollectionsTableAnnotationComposer a) f,
  ) {
    final $$BangumiCollectionsTableAnnotationComposer composer =
        $composerBuilder(
          composer: this,
          getCurrentColumn: (t) => t.accountId,
          referencedTable: $db.bangumiCollections,
          getReferencedColumn: (t) => t.accountId,
          builder:
              (
                joinBuilder, {
                $addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer,
              }) => $$BangumiCollectionsTableAnnotationComposer(
                $db: $db,
                $table: $db.bangumiCollections,
                $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
                joinBuilder: joinBuilder,
                $removeJoinBuilderFromRootComposer:
                    $removeJoinBuilderFromRootComposer,
              ),
        );
    return f(composer);
  }

  Expression<T> bangumiEpisodeCollectionsRefs<T extends Object>(
    Expression<T> Function($$BangumiEpisodeCollectionsTableAnnotationComposer a)
    f,
  ) {
    final $$BangumiEpisodeCollectionsTableAnnotationComposer composer =
        $composerBuilder(
          composer: this,
          getCurrentColumn: (t) => t.accountId,
          referencedTable: $db.bangumiEpisodeCollections,
          getReferencedColumn: (t) => t.accountId,
          builder:
              (
                joinBuilder, {
                $addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer,
              }) => $$BangumiEpisodeCollectionsTableAnnotationComposer(
                $db: $db,
                $table: $db.bangumiEpisodeCollections,
                $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
                joinBuilder: joinBuilder,
                $removeJoinBuilderFromRootComposer:
                    $removeJoinBuilderFromRootComposer,
              ),
        );
    return f(composer);
  }

  Expression<T> bangumiSyncOperationsRefs<T extends Object>(
    Expression<T> Function($$BangumiSyncOperationsTableAnnotationComposer a) f,
  ) {
    final $$BangumiSyncOperationsTableAnnotationComposer composer =
        $composerBuilder(
          composer: this,
          getCurrentColumn: (t) => t.accountId,
          referencedTable: $db.bangumiSyncOperations,
          getReferencedColumn: (t) => t.accountId,
          builder:
              (
                joinBuilder, {
                $addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer,
              }) => $$BangumiSyncOperationsTableAnnotationComposer(
                $db: $db,
                $table: $db.bangumiSyncOperations,
                $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
                joinBuilder: joinBuilder,
                $removeJoinBuilderFromRootComposer:
                    $removeJoinBuilderFromRootComposer,
              ),
        );
    return f(composer);
  }

  Expression<T> bangumiConflictSnapshotsRefs<T extends Object>(
    Expression<T> Function($$BangumiConflictSnapshotsTableAnnotationComposer a)
    f,
  ) {
    final $$BangumiConflictSnapshotsTableAnnotationComposer composer =
        $composerBuilder(
          composer: this,
          getCurrentColumn: (t) => t.accountId,
          referencedTable: $db.bangumiConflictSnapshots,
          getReferencedColumn: (t) => t.accountId,
          builder:
              (
                joinBuilder, {
                $addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer,
              }) => $$BangumiConflictSnapshotsTableAnnotationComposer(
                $db: $db,
                $table: $db.bangumiConflictSnapshots,
                $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
                joinBuilder: joinBuilder,
                $removeJoinBuilderFromRootComposer:
                    $removeJoinBuilderFromRootComposer,
              ),
        );
    return f(composer);
  }
}

class $$BangumiAccountsTableTableManager
    extends
        RootTableManager<
          _$WynimeDatabase,
          $BangumiAccountsTable,
          BangumiAccountRecord,
          $$BangumiAccountsTableFilterComposer,
          $$BangumiAccountsTableOrderingComposer,
          $$BangumiAccountsTableAnnotationComposer,
          $$BangumiAccountsTableCreateCompanionBuilder,
          $$BangumiAccountsTableUpdateCompanionBuilder,
          (BangumiAccountRecord, $$BangumiAccountsTableReferences),
          BangumiAccountRecord,
          PrefetchHooks Function({
            bool bangumiSchedulesRefs,
            bool bangumiCollectionsRefs,
            bool bangumiEpisodeCollectionsRefs,
            bool bangumiSyncOperationsRefs,
            bool bangumiConflictSnapshotsRefs,
          })
        > {
  $$BangumiAccountsTableTableManager(
    _$WynimeDatabase db,
    $BangumiAccountsTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$BangumiAccountsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$BangumiAccountsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$BangumiAccountsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> accountId = const Value.absent(),
                Value<String> username = const Value.absent(),
                Value<String?> nickname = const Value.absent(),
                Value<String?> avatarUrl = const Value.absent(),
                Value<bool> isActive = const Value.absent(),
                Value<DateTime?> scheduleUpdatedAt = const Value.absent(),
                Value<DateTime> lastSeenAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => BangumiAccountsCompanion(
                accountId: accountId,
                username: username,
                nickname: nickname,
                avatarUrl: avatarUrl,
                isActive: isActive,
                scheduleUpdatedAt: scheduleUpdatedAt,
                lastSeenAt: lastSeenAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String accountId,
                required String username,
                Value<String?> nickname = const Value.absent(),
                Value<String?> avatarUrl = const Value.absent(),
                Value<bool> isActive = const Value.absent(),
                Value<DateTime?> scheduleUpdatedAt = const Value.absent(),
                required DateTime lastSeenAt,
                Value<int> rowid = const Value.absent(),
              }) => BangumiAccountsCompanion.insert(
                accountId: accountId,
                username: username,
                nickname: nickname,
                avatarUrl: avatarUrl,
                isActive: isActive,
                scheduleUpdatedAt: scheduleUpdatedAt,
                lastSeenAt: lastSeenAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable(table),
                  $$BangumiAccountsTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback:
              ({
                bangumiSchedulesRefs = false,
                bangumiCollectionsRefs = false,
                bangumiEpisodeCollectionsRefs = false,
                bangumiSyncOperationsRefs = false,
                bangumiConflictSnapshotsRefs = false,
              }) {
                return PrefetchHooks(
                  db: db,
                  explicitlyWatchedTables: [
                    if (bangumiSchedulesRefs) db.bangumiSchedules,
                    if (bangumiCollectionsRefs) db.bangumiCollections,
                    if (bangumiEpisodeCollectionsRefs)
                      db.bangumiEpisodeCollections,
                    if (bangumiSyncOperationsRefs) db.bangumiSyncOperations,
                    if (bangumiConflictSnapshotsRefs)
                      db.bangumiConflictSnapshots,
                  ],
                  addJoins: null,
                  getPrefetchedDataCallback: (items) async {
                    return [
                      if (bangumiSchedulesRefs)
                        await $_getPrefetchedData<
                          BangumiAccountRecord,
                          $BangumiAccountsTable,
                          BangumiScheduleRecord
                        >(
                          currentTable: table,
                          referencedTable: $$BangumiAccountsTableReferences
                              ._bangumiSchedulesRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$BangumiAccountsTableReferences(
                                db,
                                table,
                                p0,
                              ).bangumiSchedulesRefs,
                          referencedItemsForCurrentItem:
                              (item, referencedItems) => referencedItems.where(
                                (e) => e.accountId == item.accountId,
                              ),
                          typedResults: items,
                        ),
                      if (bangumiCollectionsRefs)
                        await $_getPrefetchedData<
                          BangumiAccountRecord,
                          $BangumiAccountsTable,
                          BangumiCollectionRecord
                        >(
                          currentTable: table,
                          referencedTable: $$BangumiAccountsTableReferences
                              ._bangumiCollectionsRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$BangumiAccountsTableReferences(
                                db,
                                table,
                                p0,
                              ).bangumiCollectionsRefs,
                          referencedItemsForCurrentItem:
                              (item, referencedItems) => referencedItems.where(
                                (e) => e.accountId == item.accountId,
                              ),
                          typedResults: items,
                        ),
                      if (bangumiEpisodeCollectionsRefs)
                        await $_getPrefetchedData<
                          BangumiAccountRecord,
                          $BangumiAccountsTable,
                          BangumiEpisodeCollectionRecord
                        >(
                          currentTable: table,
                          referencedTable: $$BangumiAccountsTableReferences
                              ._bangumiEpisodeCollectionsRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$BangumiAccountsTableReferences(
                                db,
                                table,
                                p0,
                              ).bangumiEpisodeCollectionsRefs,
                          referencedItemsForCurrentItem:
                              (item, referencedItems) => referencedItems.where(
                                (e) => e.accountId == item.accountId,
                              ),
                          typedResults: items,
                        ),
                      if (bangumiSyncOperationsRefs)
                        await $_getPrefetchedData<
                          BangumiAccountRecord,
                          $BangumiAccountsTable,
                          BangumiSyncOperationRecord
                        >(
                          currentTable: table,
                          referencedTable: $$BangumiAccountsTableReferences
                              ._bangumiSyncOperationsRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$BangumiAccountsTableReferences(
                                db,
                                table,
                                p0,
                              ).bangumiSyncOperationsRefs,
                          referencedItemsForCurrentItem:
                              (item, referencedItems) => referencedItems.where(
                                (e) => e.accountId == item.accountId,
                              ),
                          typedResults: items,
                        ),
                      if (bangumiConflictSnapshotsRefs)
                        await $_getPrefetchedData<
                          BangumiAccountRecord,
                          $BangumiAccountsTable,
                          BangumiConflictSnapshotRecord
                        >(
                          currentTable: table,
                          referencedTable: $$BangumiAccountsTableReferences
                              ._bangumiConflictSnapshotsRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$BangumiAccountsTableReferences(
                                db,
                                table,
                                p0,
                              ).bangumiConflictSnapshotsRefs,
                          referencedItemsForCurrentItem:
                              (item, referencedItems) => referencedItems.where(
                                (e) => e.accountId == item.accountId,
                              ),
                          typedResults: items,
                        ),
                    ];
                  },
                );
              },
        ),
      );
}

typedef $$BangumiAccountsTableProcessedTableManager =
    ProcessedTableManager<
      _$WynimeDatabase,
      $BangumiAccountsTable,
      BangumiAccountRecord,
      $$BangumiAccountsTableFilterComposer,
      $$BangumiAccountsTableOrderingComposer,
      $$BangumiAccountsTableAnnotationComposer,
      $$BangumiAccountsTableCreateCompanionBuilder,
      $$BangumiAccountsTableUpdateCompanionBuilder,
      (BangumiAccountRecord, $$BangumiAccountsTableReferences),
      BangumiAccountRecord,
      PrefetchHooks Function({
        bool bangumiSchedulesRefs,
        bool bangumiCollectionsRefs,
        bool bangumiEpisodeCollectionsRefs,
        bool bangumiSyncOperationsRefs,
        bool bangumiConflictSnapshotsRefs,
      })
    >;
typedef $$BangumiSchedulesTableCreateCompanionBuilder =
    BangumiSchedulesCompanion Function({
      required String accountId,
      required String entryId,
      required String subjectId,
      required String subjectName,
      required int airWeekday,
      Value<DateTime?> airDate,
      Value<double?> episodeNumber,
      Value<String?> imageUrl,
      required DateTime updatedAt,
      Value<int> rowid,
    });
typedef $$BangumiSchedulesTableUpdateCompanionBuilder =
    BangumiSchedulesCompanion Function({
      Value<String> accountId,
      Value<String> entryId,
      Value<String> subjectId,
      Value<String> subjectName,
      Value<int> airWeekday,
      Value<DateTime?> airDate,
      Value<double?> episodeNumber,
      Value<String?> imageUrl,
      Value<DateTime> updatedAt,
      Value<int> rowid,
    });

final class $$BangumiSchedulesTableReferences
    extends
        BaseReferences<
          _$WynimeDatabase,
          $BangumiSchedulesTable,
          BangumiScheduleRecord
        > {
  $$BangumiSchedulesTableReferences(
    super.$_db,
    super.$_table,
    super.$_typedResult,
  );

  static $BangumiAccountsTable _accountIdTable(_$WynimeDatabase db) =>
      db.bangumiAccounts.createAlias(
        'bangumi_schedules__account_id__bangumi_accounts__account_id',
      );

  $$BangumiAccountsTableProcessedTableManager get accountId {
    final $_column = $_itemColumn<String>('account_id')!;

    final manager = $$BangumiAccountsTableTableManager(
      $_db,
      $_db.bangumiAccounts,
    ).filter((f) => f.accountId.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_accountIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }
}

class $$BangumiSchedulesTableFilterComposer
    extends Composer<_$WynimeDatabase, $BangumiSchedulesTable> {
  $$BangumiSchedulesTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get entryId => $composableBuilder(
    column: $table.entryId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get subjectId => $composableBuilder(
    column: $table.subjectId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get subjectName => $composableBuilder(
    column: $table.subjectName,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get airWeekday => $composableBuilder(
    column: $table.airWeekday,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get airDate => $composableBuilder(
    column: $table.airDate,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<double> get episodeNumber => $composableBuilder(
    column: $table.episodeNumber,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get imageUrl => $composableBuilder(
    column: $table.imageUrl,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnFilters(column),
  );

  $$BangumiAccountsTableFilterComposer get accountId {
    final $$BangumiAccountsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.accountId,
      referencedTable: $db.bangumiAccounts,
      getReferencedColumn: (t) => t.accountId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiAccountsTableFilterComposer(
            $db: $db,
            $table: $db.bangumiAccounts,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$BangumiSchedulesTableOrderingComposer
    extends Composer<_$WynimeDatabase, $BangumiSchedulesTable> {
  $$BangumiSchedulesTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get entryId => $composableBuilder(
    column: $table.entryId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get subjectId => $composableBuilder(
    column: $table.subjectId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get subjectName => $composableBuilder(
    column: $table.subjectName,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get airWeekday => $composableBuilder(
    column: $table.airWeekday,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get airDate => $composableBuilder(
    column: $table.airDate,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<double> get episodeNumber => $composableBuilder(
    column: $table.episodeNumber,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get imageUrl => $composableBuilder(
    column: $table.imageUrl,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnOrderings(column),
  );

  $$BangumiAccountsTableOrderingComposer get accountId {
    final $$BangumiAccountsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.accountId,
      referencedTable: $db.bangumiAccounts,
      getReferencedColumn: (t) => t.accountId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiAccountsTableOrderingComposer(
            $db: $db,
            $table: $db.bangumiAccounts,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$BangumiSchedulesTableAnnotationComposer
    extends Composer<_$WynimeDatabase, $BangumiSchedulesTable> {
  $$BangumiSchedulesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get entryId =>
      $composableBuilder(column: $table.entryId, builder: (column) => column);

  GeneratedColumn<String> get subjectId =>
      $composableBuilder(column: $table.subjectId, builder: (column) => column);

  GeneratedColumn<String> get subjectName => $composableBuilder(
    column: $table.subjectName,
    builder: (column) => column,
  );

  GeneratedColumn<int> get airWeekday => $composableBuilder(
    column: $table.airWeekday,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get airDate =>
      $composableBuilder(column: $table.airDate, builder: (column) => column);

  GeneratedColumn<double> get episodeNumber => $composableBuilder(
    column: $table.episodeNumber,
    builder: (column) => column,
  );

  GeneratedColumn<String> get imageUrl =>
      $composableBuilder(column: $table.imageUrl, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);

  $$BangumiAccountsTableAnnotationComposer get accountId {
    final $$BangumiAccountsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.accountId,
      referencedTable: $db.bangumiAccounts,
      getReferencedColumn: (t) => t.accountId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiAccountsTableAnnotationComposer(
            $db: $db,
            $table: $db.bangumiAccounts,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$BangumiSchedulesTableTableManager
    extends
        RootTableManager<
          _$WynimeDatabase,
          $BangumiSchedulesTable,
          BangumiScheduleRecord,
          $$BangumiSchedulesTableFilterComposer,
          $$BangumiSchedulesTableOrderingComposer,
          $$BangumiSchedulesTableAnnotationComposer,
          $$BangumiSchedulesTableCreateCompanionBuilder,
          $$BangumiSchedulesTableUpdateCompanionBuilder,
          (BangumiScheduleRecord, $$BangumiSchedulesTableReferences),
          BangumiScheduleRecord,
          PrefetchHooks Function({bool accountId})
        > {
  $$BangumiSchedulesTableTableManager(
    _$WynimeDatabase db,
    $BangumiSchedulesTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$BangumiSchedulesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$BangumiSchedulesTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$BangumiSchedulesTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> accountId = const Value.absent(),
                Value<String> entryId = const Value.absent(),
                Value<String> subjectId = const Value.absent(),
                Value<String> subjectName = const Value.absent(),
                Value<int> airWeekday = const Value.absent(),
                Value<DateTime?> airDate = const Value.absent(),
                Value<double?> episodeNumber = const Value.absent(),
                Value<String?> imageUrl = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => BangumiSchedulesCompanion(
                accountId: accountId,
                entryId: entryId,
                subjectId: subjectId,
                subjectName: subjectName,
                airWeekday: airWeekday,
                airDate: airDate,
                episodeNumber: episodeNumber,
                imageUrl: imageUrl,
                updatedAt: updatedAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String accountId,
                required String entryId,
                required String subjectId,
                required String subjectName,
                required int airWeekday,
                Value<DateTime?> airDate = const Value.absent(),
                Value<double?> episodeNumber = const Value.absent(),
                Value<String?> imageUrl = const Value.absent(),
                required DateTime updatedAt,
                Value<int> rowid = const Value.absent(),
              }) => BangumiSchedulesCompanion.insert(
                accountId: accountId,
                entryId: entryId,
                subjectId: subjectId,
                subjectName: subjectName,
                airWeekday: airWeekday,
                airDate: airDate,
                episodeNumber: episodeNumber,
                imageUrl: imageUrl,
                updatedAt: updatedAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable(table),
                  $$BangumiSchedulesTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({accountId = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [],
              addJoins:
                  <
                    T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic
                    >
                  >(state) {
                    if (accountId) {
                      state =
                          state.withJoin(
                                currentTable: table,
                                currentColumn: table.accountId,
                                referencedTable:
                                    $$BangumiSchedulesTableReferences
                                        ._accountIdTable(db),
                                referencedColumn:
                                    $$BangumiSchedulesTableReferences
                                        ._accountIdTable(db)
                                        .accountId,
                              )
                              as T;
                    }

                    return state;
                  },
              getPrefetchedDataCallback: (items) async {
                return [];
              },
            );
          },
        ),
      );
}

typedef $$BangumiSchedulesTableProcessedTableManager =
    ProcessedTableManager<
      _$WynimeDatabase,
      $BangumiSchedulesTable,
      BangumiScheduleRecord,
      $$BangumiSchedulesTableFilterComposer,
      $$BangumiSchedulesTableOrderingComposer,
      $$BangumiSchedulesTableAnnotationComposer,
      $$BangumiSchedulesTableCreateCompanionBuilder,
      $$BangumiSchedulesTableUpdateCompanionBuilder,
      (BangumiScheduleRecord, $$BangumiSchedulesTableReferences),
      BangumiScheduleRecord,
      PrefetchHooks Function({bool accountId})
    >;
typedef $$BangumiSubjectsTableCreateCompanionBuilder =
    BangumiSubjectsCompanion Function({
      required String subjectId,
      required String name,
      required String nameCn,
      required String summary,
      Value<String?> imageUrl,
      Value<int?> eps,
      required DateTime updatedAt,
      Value<int> rowid,
    });
typedef $$BangumiSubjectsTableUpdateCompanionBuilder =
    BangumiSubjectsCompanion Function({
      Value<String> subjectId,
      Value<String> name,
      Value<String> nameCn,
      Value<String> summary,
      Value<String?> imageUrl,
      Value<int?> eps,
      Value<DateTime> updatedAt,
      Value<int> rowid,
    });

final class $$BangumiSubjectsTableReferences
    extends
        BaseReferences<
          _$WynimeDatabase,
          $BangumiSubjectsTable,
          BangumiSubjectRecord
        > {
  $$BangumiSubjectsTableReferences(
    super.$_db,
    super.$_table,
    super.$_typedResult,
  );

  static MultiTypedResultKey<
    $BangumiCollectionsTable,
    List<BangumiCollectionRecord>
  >
  _bangumiCollectionsRefsTable(_$WynimeDatabase db) =>
      MultiTypedResultKey.fromTable(
        db.bangumiCollections,
        aliasName:
            'bangumi_subjects__subject_id__bangumi_collections__subject_id',
      );

  $$BangumiCollectionsTableProcessedTableManager get bangumiCollectionsRefs {
    final manager =
        $$BangumiCollectionsTableTableManager(
          $_db,
          $_db.bangumiCollections,
        ).filter(
          (f) => f.subjectId.subjectId.sqlEquals(
            $_itemColumn<String>('subject_id')!,
          ),
        );

    final cache = $_typedResult.readTableOrNull(
      _bangumiCollectionsRefsTable($_db),
    );
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }

  static MultiTypedResultKey<$BangumiEpisodesTable, List<BangumiEpisodeRecord>>
  _bangumiEpisodesRefsTable(_$WynimeDatabase db) =>
      MultiTypedResultKey.fromTable(
        db.bangumiEpisodes,
        aliasName: 'bangumi_subjects__subject_id__bangumi_episodes__subject_id',
      );

  $$BangumiEpisodesTableProcessedTableManager get bangumiEpisodesRefs {
    final manager =
        $$BangumiEpisodesTableTableManager($_db, $_db.bangumiEpisodes).filter(
          (f) => f.subjectId.subjectId.sqlEquals(
            $_itemColumn<String>('subject_id')!,
          ),
        );

    final cache = $_typedResult.readTableOrNull(
      _bangumiEpisodesRefsTable($_db),
    );
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }

  static MultiTypedResultKey<$BangumiMappingsTable, List<BangumiMappingRecord>>
  _bangumiMappingsRefsTable(
    _$WynimeDatabase db,
  ) => MultiTypedResultKey.fromTable(
    db.bangumiMappings,
    aliasName:
        'bangumi_subjects__subject_id__bangumi_mappings__bangumi_subject_id',
  );

  $$BangumiMappingsTableProcessedTableManager get bangumiMappingsRefs {
    final manager =
        $$BangumiMappingsTableTableManager($_db, $_db.bangumiMappings).filter(
          (f) => f.bangumiSubjectId.subjectId.sqlEquals(
            $_itemColumn<String>('subject_id')!,
          ),
        );

    final cache = $_typedResult.readTableOrNull(
      _bangumiMappingsRefsTable($_db),
    );
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }

  static MultiTypedResultKey<
    $BangumiSyncOperationsTable,
    List<BangumiSyncOperationRecord>
  >
  _bangumiSyncOperationsRefsTable(_$WynimeDatabase db) =>
      MultiTypedResultKey.fromTable(
        db.bangumiSyncOperations,
        aliasName:
            'bangumi_subjects__subject_id__bangumi_sync_operations__subject_id',
      );

  $$BangumiSyncOperationsTableProcessedTableManager
  get bangumiSyncOperationsRefs {
    final manager =
        $$BangumiSyncOperationsTableTableManager(
          $_db,
          $_db.bangumiSyncOperations,
        ).filter(
          (f) => f.subjectId.subjectId.sqlEquals(
            $_itemColumn<String>('subject_id')!,
          ),
        );

    final cache = $_typedResult.readTableOrNull(
      _bangumiSyncOperationsRefsTable($_db),
    );
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }

  static MultiTypedResultKey<
    $BangumiConflictSnapshotsTable,
    List<BangumiConflictSnapshotRecord>
  >
  _bangumiConflictSnapshotsRefsTable(
    _$WynimeDatabase db,
  ) => MultiTypedResultKey.fromTable(
    db.bangumiConflictSnapshots,
    aliasName:
        'bangumi_subjects__subject_id__bangumi_conflict_snapshots__subject_id',
  );

  $$BangumiConflictSnapshotsTableProcessedTableManager
  get bangumiConflictSnapshotsRefs {
    final manager =
        $$BangumiConflictSnapshotsTableTableManager(
          $_db,
          $_db.bangumiConflictSnapshots,
        ).filter(
          (f) => f.subjectId.subjectId.sqlEquals(
            $_itemColumn<String>('subject_id')!,
          ),
        );

    final cache = $_typedResult.readTableOrNull(
      _bangumiConflictSnapshotsRefsTable($_db),
    );
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }
}

class $$BangumiSubjectsTableFilterComposer
    extends Composer<_$WynimeDatabase, $BangumiSubjectsTable> {
  $$BangumiSubjectsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get subjectId => $composableBuilder(
    column: $table.subjectId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get name => $composableBuilder(
    column: $table.name,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get nameCn => $composableBuilder(
    column: $table.nameCn,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get summary => $composableBuilder(
    column: $table.summary,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get imageUrl => $composableBuilder(
    column: $table.imageUrl,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get eps => $composableBuilder(
    column: $table.eps,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnFilters(column),
  );

  Expression<bool> bangumiCollectionsRefs(
    Expression<bool> Function($$BangumiCollectionsTableFilterComposer f) f,
  ) {
    final $$BangumiCollectionsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.subjectId,
      referencedTable: $db.bangumiCollections,
      getReferencedColumn: (t) => t.subjectId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiCollectionsTableFilterComposer(
            $db: $db,
            $table: $db.bangumiCollections,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<bool> bangumiEpisodesRefs(
    Expression<bool> Function($$BangumiEpisodesTableFilterComposer f) f,
  ) {
    final $$BangumiEpisodesTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.subjectId,
      referencedTable: $db.bangumiEpisodes,
      getReferencedColumn: (t) => t.subjectId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiEpisodesTableFilterComposer(
            $db: $db,
            $table: $db.bangumiEpisodes,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<bool> bangumiMappingsRefs(
    Expression<bool> Function($$BangumiMappingsTableFilterComposer f) f,
  ) {
    final $$BangumiMappingsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.subjectId,
      referencedTable: $db.bangumiMappings,
      getReferencedColumn: (t) => t.bangumiSubjectId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiMappingsTableFilterComposer(
            $db: $db,
            $table: $db.bangumiMappings,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<bool> bangumiSyncOperationsRefs(
    Expression<bool> Function($$BangumiSyncOperationsTableFilterComposer f) f,
  ) {
    final $$BangumiSyncOperationsTableFilterComposer composer =
        $composerBuilder(
          composer: this,
          getCurrentColumn: (t) => t.subjectId,
          referencedTable: $db.bangumiSyncOperations,
          getReferencedColumn: (t) => t.subjectId,
          builder:
              (
                joinBuilder, {
                $addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer,
              }) => $$BangumiSyncOperationsTableFilterComposer(
                $db: $db,
                $table: $db.bangumiSyncOperations,
                $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
                joinBuilder: joinBuilder,
                $removeJoinBuilderFromRootComposer:
                    $removeJoinBuilderFromRootComposer,
              ),
        );
    return f(composer);
  }

  Expression<bool> bangumiConflictSnapshotsRefs(
    Expression<bool> Function($$BangumiConflictSnapshotsTableFilterComposer f)
    f,
  ) {
    final $$BangumiConflictSnapshotsTableFilterComposer composer =
        $composerBuilder(
          composer: this,
          getCurrentColumn: (t) => t.subjectId,
          referencedTable: $db.bangumiConflictSnapshots,
          getReferencedColumn: (t) => t.subjectId,
          builder:
              (
                joinBuilder, {
                $addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer,
              }) => $$BangumiConflictSnapshotsTableFilterComposer(
                $db: $db,
                $table: $db.bangumiConflictSnapshots,
                $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
                joinBuilder: joinBuilder,
                $removeJoinBuilderFromRootComposer:
                    $removeJoinBuilderFromRootComposer,
              ),
        );
    return f(composer);
  }
}

class $$BangumiSubjectsTableOrderingComposer
    extends Composer<_$WynimeDatabase, $BangumiSubjectsTable> {
  $$BangumiSubjectsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get subjectId => $composableBuilder(
    column: $table.subjectId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get name => $composableBuilder(
    column: $table.name,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get nameCn => $composableBuilder(
    column: $table.nameCn,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get summary => $composableBuilder(
    column: $table.summary,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get imageUrl => $composableBuilder(
    column: $table.imageUrl,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get eps => $composableBuilder(
    column: $table.eps,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$BangumiSubjectsTableAnnotationComposer
    extends Composer<_$WynimeDatabase, $BangumiSubjectsTable> {
  $$BangumiSubjectsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get subjectId =>
      $composableBuilder(column: $table.subjectId, builder: (column) => column);

  GeneratedColumn<String> get name =>
      $composableBuilder(column: $table.name, builder: (column) => column);

  GeneratedColumn<String> get nameCn =>
      $composableBuilder(column: $table.nameCn, builder: (column) => column);

  GeneratedColumn<String> get summary =>
      $composableBuilder(column: $table.summary, builder: (column) => column);

  GeneratedColumn<String> get imageUrl =>
      $composableBuilder(column: $table.imageUrl, builder: (column) => column);

  GeneratedColumn<int> get eps =>
      $composableBuilder(column: $table.eps, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);

  Expression<T> bangumiCollectionsRefs<T extends Object>(
    Expression<T> Function($$BangumiCollectionsTableAnnotationComposer a) f,
  ) {
    final $$BangumiCollectionsTableAnnotationComposer composer =
        $composerBuilder(
          composer: this,
          getCurrentColumn: (t) => t.subjectId,
          referencedTable: $db.bangumiCollections,
          getReferencedColumn: (t) => t.subjectId,
          builder:
              (
                joinBuilder, {
                $addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer,
              }) => $$BangumiCollectionsTableAnnotationComposer(
                $db: $db,
                $table: $db.bangumiCollections,
                $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
                joinBuilder: joinBuilder,
                $removeJoinBuilderFromRootComposer:
                    $removeJoinBuilderFromRootComposer,
              ),
        );
    return f(composer);
  }

  Expression<T> bangumiEpisodesRefs<T extends Object>(
    Expression<T> Function($$BangumiEpisodesTableAnnotationComposer a) f,
  ) {
    final $$BangumiEpisodesTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.subjectId,
      referencedTable: $db.bangumiEpisodes,
      getReferencedColumn: (t) => t.subjectId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiEpisodesTableAnnotationComposer(
            $db: $db,
            $table: $db.bangumiEpisodes,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<T> bangumiMappingsRefs<T extends Object>(
    Expression<T> Function($$BangumiMappingsTableAnnotationComposer a) f,
  ) {
    final $$BangumiMappingsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.subjectId,
      referencedTable: $db.bangumiMappings,
      getReferencedColumn: (t) => t.bangumiSubjectId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiMappingsTableAnnotationComposer(
            $db: $db,
            $table: $db.bangumiMappings,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<T> bangumiSyncOperationsRefs<T extends Object>(
    Expression<T> Function($$BangumiSyncOperationsTableAnnotationComposer a) f,
  ) {
    final $$BangumiSyncOperationsTableAnnotationComposer composer =
        $composerBuilder(
          composer: this,
          getCurrentColumn: (t) => t.subjectId,
          referencedTable: $db.bangumiSyncOperations,
          getReferencedColumn: (t) => t.subjectId,
          builder:
              (
                joinBuilder, {
                $addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer,
              }) => $$BangumiSyncOperationsTableAnnotationComposer(
                $db: $db,
                $table: $db.bangumiSyncOperations,
                $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
                joinBuilder: joinBuilder,
                $removeJoinBuilderFromRootComposer:
                    $removeJoinBuilderFromRootComposer,
              ),
        );
    return f(composer);
  }

  Expression<T> bangumiConflictSnapshotsRefs<T extends Object>(
    Expression<T> Function($$BangumiConflictSnapshotsTableAnnotationComposer a)
    f,
  ) {
    final $$BangumiConflictSnapshotsTableAnnotationComposer composer =
        $composerBuilder(
          composer: this,
          getCurrentColumn: (t) => t.subjectId,
          referencedTable: $db.bangumiConflictSnapshots,
          getReferencedColumn: (t) => t.subjectId,
          builder:
              (
                joinBuilder, {
                $addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer,
              }) => $$BangumiConflictSnapshotsTableAnnotationComposer(
                $db: $db,
                $table: $db.bangumiConflictSnapshots,
                $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
                joinBuilder: joinBuilder,
                $removeJoinBuilderFromRootComposer:
                    $removeJoinBuilderFromRootComposer,
              ),
        );
    return f(composer);
  }
}

class $$BangumiSubjectsTableTableManager
    extends
        RootTableManager<
          _$WynimeDatabase,
          $BangumiSubjectsTable,
          BangumiSubjectRecord,
          $$BangumiSubjectsTableFilterComposer,
          $$BangumiSubjectsTableOrderingComposer,
          $$BangumiSubjectsTableAnnotationComposer,
          $$BangumiSubjectsTableCreateCompanionBuilder,
          $$BangumiSubjectsTableUpdateCompanionBuilder,
          (BangumiSubjectRecord, $$BangumiSubjectsTableReferences),
          BangumiSubjectRecord,
          PrefetchHooks Function({
            bool bangumiCollectionsRefs,
            bool bangumiEpisodesRefs,
            bool bangumiMappingsRefs,
            bool bangumiSyncOperationsRefs,
            bool bangumiConflictSnapshotsRefs,
          })
        > {
  $$BangumiSubjectsTableTableManager(
    _$WynimeDatabase db,
    $BangumiSubjectsTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$BangumiSubjectsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$BangumiSubjectsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$BangumiSubjectsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> subjectId = const Value.absent(),
                Value<String> name = const Value.absent(),
                Value<String> nameCn = const Value.absent(),
                Value<String> summary = const Value.absent(),
                Value<String?> imageUrl = const Value.absent(),
                Value<int?> eps = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => BangumiSubjectsCompanion(
                subjectId: subjectId,
                name: name,
                nameCn: nameCn,
                summary: summary,
                imageUrl: imageUrl,
                eps: eps,
                updatedAt: updatedAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String subjectId,
                required String name,
                required String nameCn,
                required String summary,
                Value<String?> imageUrl = const Value.absent(),
                Value<int?> eps = const Value.absent(),
                required DateTime updatedAt,
                Value<int> rowid = const Value.absent(),
              }) => BangumiSubjectsCompanion.insert(
                subjectId: subjectId,
                name: name,
                nameCn: nameCn,
                summary: summary,
                imageUrl: imageUrl,
                eps: eps,
                updatedAt: updatedAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable(table),
                  $$BangumiSubjectsTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback:
              ({
                bangumiCollectionsRefs = false,
                bangumiEpisodesRefs = false,
                bangumiMappingsRefs = false,
                bangumiSyncOperationsRefs = false,
                bangumiConflictSnapshotsRefs = false,
              }) {
                return PrefetchHooks(
                  db: db,
                  explicitlyWatchedTables: [
                    if (bangumiCollectionsRefs) db.bangumiCollections,
                    if (bangumiEpisodesRefs) db.bangumiEpisodes,
                    if (bangumiMappingsRefs) db.bangumiMappings,
                    if (bangumiSyncOperationsRefs) db.bangumiSyncOperations,
                    if (bangumiConflictSnapshotsRefs)
                      db.bangumiConflictSnapshots,
                  ],
                  addJoins: null,
                  getPrefetchedDataCallback: (items) async {
                    return [
                      if (bangumiCollectionsRefs)
                        await $_getPrefetchedData<
                          BangumiSubjectRecord,
                          $BangumiSubjectsTable,
                          BangumiCollectionRecord
                        >(
                          currentTable: table,
                          referencedTable: $$BangumiSubjectsTableReferences
                              ._bangumiCollectionsRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$BangumiSubjectsTableReferences(
                                db,
                                table,
                                p0,
                              ).bangumiCollectionsRefs,
                          referencedItemsForCurrentItem:
                              (item, referencedItems) => referencedItems.where(
                                (e) => e.subjectId == item.subjectId,
                              ),
                          typedResults: items,
                        ),
                      if (bangumiEpisodesRefs)
                        await $_getPrefetchedData<
                          BangumiSubjectRecord,
                          $BangumiSubjectsTable,
                          BangumiEpisodeRecord
                        >(
                          currentTable: table,
                          referencedTable: $$BangumiSubjectsTableReferences
                              ._bangumiEpisodesRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$BangumiSubjectsTableReferences(
                                db,
                                table,
                                p0,
                              ).bangumiEpisodesRefs,
                          referencedItemsForCurrentItem:
                              (item, referencedItems) => referencedItems.where(
                                (e) => e.subjectId == item.subjectId,
                              ),
                          typedResults: items,
                        ),
                      if (bangumiMappingsRefs)
                        await $_getPrefetchedData<
                          BangumiSubjectRecord,
                          $BangumiSubjectsTable,
                          BangumiMappingRecord
                        >(
                          currentTable: table,
                          referencedTable: $$BangumiSubjectsTableReferences
                              ._bangumiMappingsRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$BangumiSubjectsTableReferences(
                                db,
                                table,
                                p0,
                              ).bangumiMappingsRefs,
                          referencedItemsForCurrentItem:
                              (item, referencedItems) => referencedItems.where(
                                (e) => e.bangumiSubjectId == item.subjectId,
                              ),
                          typedResults: items,
                        ),
                      if (bangumiSyncOperationsRefs)
                        await $_getPrefetchedData<
                          BangumiSubjectRecord,
                          $BangumiSubjectsTable,
                          BangumiSyncOperationRecord
                        >(
                          currentTable: table,
                          referencedTable: $$BangumiSubjectsTableReferences
                              ._bangumiSyncOperationsRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$BangumiSubjectsTableReferences(
                                db,
                                table,
                                p0,
                              ).bangumiSyncOperationsRefs,
                          referencedItemsForCurrentItem:
                              (item, referencedItems) => referencedItems.where(
                                (e) => e.subjectId == item.subjectId,
                              ),
                          typedResults: items,
                        ),
                      if (bangumiConflictSnapshotsRefs)
                        await $_getPrefetchedData<
                          BangumiSubjectRecord,
                          $BangumiSubjectsTable,
                          BangumiConflictSnapshotRecord
                        >(
                          currentTable: table,
                          referencedTable: $$BangumiSubjectsTableReferences
                              ._bangumiConflictSnapshotsRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$BangumiSubjectsTableReferences(
                                db,
                                table,
                                p0,
                              ).bangumiConflictSnapshotsRefs,
                          referencedItemsForCurrentItem:
                              (item, referencedItems) => referencedItems.where(
                                (e) => e.subjectId == item.subjectId,
                              ),
                          typedResults: items,
                        ),
                    ];
                  },
                );
              },
        ),
      );
}

typedef $$BangumiSubjectsTableProcessedTableManager =
    ProcessedTableManager<
      _$WynimeDatabase,
      $BangumiSubjectsTable,
      BangumiSubjectRecord,
      $$BangumiSubjectsTableFilterComposer,
      $$BangumiSubjectsTableOrderingComposer,
      $$BangumiSubjectsTableAnnotationComposer,
      $$BangumiSubjectsTableCreateCompanionBuilder,
      $$BangumiSubjectsTableUpdateCompanionBuilder,
      (BangumiSubjectRecord, $$BangumiSubjectsTableReferences),
      BangumiSubjectRecord,
      PrefetchHooks Function({
        bool bangumiCollectionsRefs,
        bool bangumiEpisodesRefs,
        bool bangumiMappingsRefs,
        bool bangumiSyncOperationsRefs,
        bool bangumiConflictSnapshotsRefs,
      })
    >;
typedef $$BangumiCollectionsTableCreateCompanionBuilder =
    BangumiCollectionsCompanion Function({
      required String accountId,
      required String subjectId,
      Value<int?> status,
      Value<String?> remoteRevision,
      required DateTime localUpdatedAt,
      Value<DateTime?> remoteUpdatedAt,
      Value<int> rowid,
    });
typedef $$BangumiCollectionsTableUpdateCompanionBuilder =
    BangumiCollectionsCompanion Function({
      Value<String> accountId,
      Value<String> subjectId,
      Value<int?> status,
      Value<String?> remoteRevision,
      Value<DateTime> localUpdatedAt,
      Value<DateTime?> remoteUpdatedAt,
      Value<int> rowid,
    });

final class $$BangumiCollectionsTableReferences
    extends
        BaseReferences<
          _$WynimeDatabase,
          $BangumiCollectionsTable,
          BangumiCollectionRecord
        > {
  $$BangumiCollectionsTableReferences(
    super.$_db,
    super.$_table,
    super.$_typedResult,
  );

  static $BangumiAccountsTable _accountIdTable(_$WynimeDatabase db) =>
      db.bangumiAccounts.createAlias(
        'bangumi_collections__account_id__bangumi_accounts__account_id',
      );

  $$BangumiAccountsTableProcessedTableManager get accountId {
    final $_column = $_itemColumn<String>('account_id')!;

    final manager = $$BangumiAccountsTableTableManager(
      $_db,
      $_db.bangumiAccounts,
    ).filter((f) => f.accountId.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_accountIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }

  static $BangumiSubjectsTable _subjectIdTable(_$WynimeDatabase db) =>
      db.bangumiSubjects.createAlias(
        'bangumi_collections__subject_id__bangumi_subjects__subject_id',
      );

  $$BangumiSubjectsTableProcessedTableManager get subjectId {
    final $_column = $_itemColumn<String>('subject_id')!;

    final manager = $$BangumiSubjectsTableTableManager(
      $_db,
      $_db.bangumiSubjects,
    ).filter((f) => f.subjectId.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_subjectIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }
}

class $$BangumiCollectionsTableFilterComposer
    extends Composer<_$WynimeDatabase, $BangumiCollectionsTable> {
  $$BangumiCollectionsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get remoteRevision => $composableBuilder(
    column: $table.remoteRevision,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get localUpdatedAt => $composableBuilder(
    column: $table.localUpdatedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get remoteUpdatedAt => $composableBuilder(
    column: $table.remoteUpdatedAt,
    builder: (column) => ColumnFilters(column),
  );

  $$BangumiAccountsTableFilterComposer get accountId {
    final $$BangumiAccountsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.accountId,
      referencedTable: $db.bangumiAccounts,
      getReferencedColumn: (t) => t.accountId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiAccountsTableFilterComposer(
            $db: $db,
            $table: $db.bangumiAccounts,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$BangumiSubjectsTableFilterComposer get subjectId {
    final $$BangumiSubjectsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.subjectId,
      referencedTable: $db.bangumiSubjects,
      getReferencedColumn: (t) => t.subjectId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiSubjectsTableFilterComposer(
            $db: $db,
            $table: $db.bangumiSubjects,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$BangumiCollectionsTableOrderingComposer
    extends Composer<_$WynimeDatabase, $BangumiCollectionsTable> {
  $$BangumiCollectionsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get remoteRevision => $composableBuilder(
    column: $table.remoteRevision,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get localUpdatedAt => $composableBuilder(
    column: $table.localUpdatedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get remoteUpdatedAt => $composableBuilder(
    column: $table.remoteUpdatedAt,
    builder: (column) => ColumnOrderings(column),
  );

  $$BangumiAccountsTableOrderingComposer get accountId {
    final $$BangumiAccountsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.accountId,
      referencedTable: $db.bangumiAccounts,
      getReferencedColumn: (t) => t.accountId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiAccountsTableOrderingComposer(
            $db: $db,
            $table: $db.bangumiAccounts,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$BangumiSubjectsTableOrderingComposer get subjectId {
    final $$BangumiSubjectsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.subjectId,
      referencedTable: $db.bangumiSubjects,
      getReferencedColumn: (t) => t.subjectId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiSubjectsTableOrderingComposer(
            $db: $db,
            $table: $db.bangumiSubjects,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$BangumiCollectionsTableAnnotationComposer
    extends Composer<_$WynimeDatabase, $BangumiCollectionsTable> {
  $$BangumiCollectionsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get status =>
      $composableBuilder(column: $table.status, builder: (column) => column);

  GeneratedColumn<String> get remoteRevision => $composableBuilder(
    column: $table.remoteRevision,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get localUpdatedAt => $composableBuilder(
    column: $table.localUpdatedAt,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get remoteUpdatedAt => $composableBuilder(
    column: $table.remoteUpdatedAt,
    builder: (column) => column,
  );

  $$BangumiAccountsTableAnnotationComposer get accountId {
    final $$BangumiAccountsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.accountId,
      referencedTable: $db.bangumiAccounts,
      getReferencedColumn: (t) => t.accountId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiAccountsTableAnnotationComposer(
            $db: $db,
            $table: $db.bangumiAccounts,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$BangumiSubjectsTableAnnotationComposer get subjectId {
    final $$BangumiSubjectsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.subjectId,
      referencedTable: $db.bangumiSubjects,
      getReferencedColumn: (t) => t.subjectId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiSubjectsTableAnnotationComposer(
            $db: $db,
            $table: $db.bangumiSubjects,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$BangumiCollectionsTableTableManager
    extends
        RootTableManager<
          _$WynimeDatabase,
          $BangumiCollectionsTable,
          BangumiCollectionRecord,
          $$BangumiCollectionsTableFilterComposer,
          $$BangumiCollectionsTableOrderingComposer,
          $$BangumiCollectionsTableAnnotationComposer,
          $$BangumiCollectionsTableCreateCompanionBuilder,
          $$BangumiCollectionsTableUpdateCompanionBuilder,
          (BangumiCollectionRecord, $$BangumiCollectionsTableReferences),
          BangumiCollectionRecord,
          PrefetchHooks Function({bool accountId, bool subjectId})
        > {
  $$BangumiCollectionsTableTableManager(
    _$WynimeDatabase db,
    $BangumiCollectionsTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$BangumiCollectionsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$BangumiCollectionsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$BangumiCollectionsTableAnnotationComposer(
                $db: db,
                $table: table,
              ),
          updateCompanionCallback:
              ({
                Value<String> accountId = const Value.absent(),
                Value<String> subjectId = const Value.absent(),
                Value<int?> status = const Value.absent(),
                Value<String?> remoteRevision = const Value.absent(),
                Value<DateTime> localUpdatedAt = const Value.absent(),
                Value<DateTime?> remoteUpdatedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => BangumiCollectionsCompanion(
                accountId: accountId,
                subjectId: subjectId,
                status: status,
                remoteRevision: remoteRevision,
                localUpdatedAt: localUpdatedAt,
                remoteUpdatedAt: remoteUpdatedAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String accountId,
                required String subjectId,
                Value<int?> status = const Value.absent(),
                Value<String?> remoteRevision = const Value.absent(),
                required DateTime localUpdatedAt,
                Value<DateTime?> remoteUpdatedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => BangumiCollectionsCompanion.insert(
                accountId: accountId,
                subjectId: subjectId,
                status: status,
                remoteRevision: remoteRevision,
                localUpdatedAt: localUpdatedAt,
                remoteUpdatedAt: remoteUpdatedAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable(table),
                  $$BangumiCollectionsTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({accountId = false, subjectId = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [],
              addJoins:
                  <
                    T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic
                    >
                  >(state) {
                    if (accountId) {
                      state =
                          state.withJoin(
                                currentTable: table,
                                currentColumn: table.accountId,
                                referencedTable:
                                    $$BangumiCollectionsTableReferences
                                        ._accountIdTable(db),
                                referencedColumn:
                                    $$BangumiCollectionsTableReferences
                                        ._accountIdTable(db)
                                        .accountId,
                              )
                              as T;
                    }
                    if (subjectId) {
                      state =
                          state.withJoin(
                                currentTable: table,
                                currentColumn: table.subjectId,
                                referencedTable:
                                    $$BangumiCollectionsTableReferences
                                        ._subjectIdTable(db),
                                referencedColumn:
                                    $$BangumiCollectionsTableReferences
                                        ._subjectIdTable(db)
                                        .subjectId,
                              )
                              as T;
                    }

                    return state;
                  },
              getPrefetchedDataCallback: (items) async {
                return [];
              },
            );
          },
        ),
      );
}

typedef $$BangumiCollectionsTableProcessedTableManager =
    ProcessedTableManager<
      _$WynimeDatabase,
      $BangumiCollectionsTable,
      BangumiCollectionRecord,
      $$BangumiCollectionsTableFilterComposer,
      $$BangumiCollectionsTableOrderingComposer,
      $$BangumiCollectionsTableAnnotationComposer,
      $$BangumiCollectionsTableCreateCompanionBuilder,
      $$BangumiCollectionsTableUpdateCompanionBuilder,
      (BangumiCollectionRecord, $$BangumiCollectionsTableReferences),
      BangumiCollectionRecord,
      PrefetchHooks Function({bool accountId, bool subjectId})
    >;
typedef $$BangumiEpisodesTableCreateCompanionBuilder =
    BangumiEpisodesCompanion Function({
      required String episodeId,
      required String subjectId,
      required String name,
      required String nameCn,
      required double sort,
      required int type,
      Value<int?> duration,
      required DateTime updatedAt,
      Value<int> rowid,
    });
typedef $$BangumiEpisodesTableUpdateCompanionBuilder =
    BangumiEpisodesCompanion Function({
      Value<String> episodeId,
      Value<String> subjectId,
      Value<String> name,
      Value<String> nameCn,
      Value<double> sort,
      Value<int> type,
      Value<int?> duration,
      Value<DateTime> updatedAt,
      Value<int> rowid,
    });

final class $$BangumiEpisodesTableReferences
    extends
        BaseReferences<
          _$WynimeDatabase,
          $BangumiEpisodesTable,
          BangumiEpisodeRecord
        > {
  $$BangumiEpisodesTableReferences(
    super.$_db,
    super.$_table,
    super.$_typedResult,
  );

  static $BangumiSubjectsTable _subjectIdTable(_$WynimeDatabase db) =>
      db.bangumiSubjects.createAlias(
        'bangumi_episodes__subject_id__bangumi_subjects__subject_id',
      );

  $$BangumiSubjectsTableProcessedTableManager get subjectId {
    final $_column = $_itemColumn<String>('subject_id')!;

    final manager = $$BangumiSubjectsTableTableManager(
      $_db,
      $_db.bangumiSubjects,
    ).filter((f) => f.subjectId.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_subjectIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }

  static MultiTypedResultKey<
    $BangumiEpisodeCollectionsTable,
    List<BangumiEpisodeCollectionRecord>
  >
  _bangumiEpisodeCollectionsRefsTable(
    _$WynimeDatabase db,
  ) => MultiTypedResultKey.fromTable(
    db.bangumiEpisodeCollections,
    aliasName:
        'bangumi_episodes__episode_id__bangumi_episode_collections__episode_id',
  );

  $$BangumiEpisodeCollectionsTableProcessedTableManager
  get bangumiEpisodeCollectionsRefs {
    final manager =
        $$BangumiEpisodeCollectionsTableTableManager(
          $_db,
          $_db.bangumiEpisodeCollections,
        ).filter(
          (f) => f.episodeId.episodeId.sqlEquals(
            $_itemColumn<String>('episode_id')!,
          ),
        );

    final cache = $_typedResult.readTableOrNull(
      _bangumiEpisodeCollectionsRefsTable($_db),
    );
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }

  static MultiTypedResultKey<
    $BangumiSyncOperationsTable,
    List<BangumiSyncOperationRecord>
  >
  _bangumiSyncOperationsRefsTable(_$WynimeDatabase db) =>
      MultiTypedResultKey.fromTable(
        db.bangumiSyncOperations,
        aliasName:
            'bangumi_episodes__episode_id__bangumi_sync_operations__episode_id',
      );

  $$BangumiSyncOperationsTableProcessedTableManager
  get bangumiSyncOperationsRefs {
    final manager =
        $$BangumiSyncOperationsTableTableManager(
          $_db,
          $_db.bangumiSyncOperations,
        ).filter(
          (f) => f.episodeId.episodeId.sqlEquals(
            $_itemColumn<String>('episode_id')!,
          ),
        );

    final cache = $_typedResult.readTableOrNull(
      _bangumiSyncOperationsRefsTable($_db),
    );
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }
}

class $$BangumiEpisodesTableFilterComposer
    extends Composer<_$WynimeDatabase, $BangumiEpisodesTable> {
  $$BangumiEpisodesTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get episodeId => $composableBuilder(
    column: $table.episodeId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get name => $composableBuilder(
    column: $table.name,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get nameCn => $composableBuilder(
    column: $table.nameCn,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<double> get sort => $composableBuilder(
    column: $table.sort,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get type => $composableBuilder(
    column: $table.type,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get duration => $composableBuilder(
    column: $table.duration,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnFilters(column),
  );

  $$BangumiSubjectsTableFilterComposer get subjectId {
    final $$BangumiSubjectsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.subjectId,
      referencedTable: $db.bangumiSubjects,
      getReferencedColumn: (t) => t.subjectId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiSubjectsTableFilterComposer(
            $db: $db,
            $table: $db.bangumiSubjects,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  Expression<bool> bangumiEpisodeCollectionsRefs(
    Expression<bool> Function($$BangumiEpisodeCollectionsTableFilterComposer f)
    f,
  ) {
    final $$BangumiEpisodeCollectionsTableFilterComposer composer =
        $composerBuilder(
          composer: this,
          getCurrentColumn: (t) => t.episodeId,
          referencedTable: $db.bangumiEpisodeCollections,
          getReferencedColumn: (t) => t.episodeId,
          builder:
              (
                joinBuilder, {
                $addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer,
              }) => $$BangumiEpisodeCollectionsTableFilterComposer(
                $db: $db,
                $table: $db.bangumiEpisodeCollections,
                $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
                joinBuilder: joinBuilder,
                $removeJoinBuilderFromRootComposer:
                    $removeJoinBuilderFromRootComposer,
              ),
        );
    return f(composer);
  }

  Expression<bool> bangumiSyncOperationsRefs(
    Expression<bool> Function($$BangumiSyncOperationsTableFilterComposer f) f,
  ) {
    final $$BangumiSyncOperationsTableFilterComposer composer =
        $composerBuilder(
          composer: this,
          getCurrentColumn: (t) => t.episodeId,
          referencedTable: $db.bangumiSyncOperations,
          getReferencedColumn: (t) => t.episodeId,
          builder:
              (
                joinBuilder, {
                $addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer,
              }) => $$BangumiSyncOperationsTableFilterComposer(
                $db: $db,
                $table: $db.bangumiSyncOperations,
                $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
                joinBuilder: joinBuilder,
                $removeJoinBuilderFromRootComposer:
                    $removeJoinBuilderFromRootComposer,
              ),
        );
    return f(composer);
  }
}

class $$BangumiEpisodesTableOrderingComposer
    extends Composer<_$WynimeDatabase, $BangumiEpisodesTable> {
  $$BangumiEpisodesTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get episodeId => $composableBuilder(
    column: $table.episodeId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get name => $composableBuilder(
    column: $table.name,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get nameCn => $composableBuilder(
    column: $table.nameCn,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<double> get sort => $composableBuilder(
    column: $table.sort,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get type => $composableBuilder(
    column: $table.type,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get duration => $composableBuilder(
    column: $table.duration,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnOrderings(column),
  );

  $$BangumiSubjectsTableOrderingComposer get subjectId {
    final $$BangumiSubjectsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.subjectId,
      referencedTable: $db.bangumiSubjects,
      getReferencedColumn: (t) => t.subjectId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiSubjectsTableOrderingComposer(
            $db: $db,
            $table: $db.bangumiSubjects,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$BangumiEpisodesTableAnnotationComposer
    extends Composer<_$WynimeDatabase, $BangumiEpisodesTable> {
  $$BangumiEpisodesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get episodeId =>
      $composableBuilder(column: $table.episodeId, builder: (column) => column);

  GeneratedColumn<String> get name =>
      $composableBuilder(column: $table.name, builder: (column) => column);

  GeneratedColumn<String> get nameCn =>
      $composableBuilder(column: $table.nameCn, builder: (column) => column);

  GeneratedColumn<double> get sort =>
      $composableBuilder(column: $table.sort, builder: (column) => column);

  GeneratedColumn<int> get type =>
      $composableBuilder(column: $table.type, builder: (column) => column);

  GeneratedColumn<int> get duration =>
      $composableBuilder(column: $table.duration, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);

  $$BangumiSubjectsTableAnnotationComposer get subjectId {
    final $$BangumiSubjectsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.subjectId,
      referencedTable: $db.bangumiSubjects,
      getReferencedColumn: (t) => t.subjectId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiSubjectsTableAnnotationComposer(
            $db: $db,
            $table: $db.bangumiSubjects,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  Expression<T> bangumiEpisodeCollectionsRefs<T extends Object>(
    Expression<T> Function($$BangumiEpisodeCollectionsTableAnnotationComposer a)
    f,
  ) {
    final $$BangumiEpisodeCollectionsTableAnnotationComposer composer =
        $composerBuilder(
          composer: this,
          getCurrentColumn: (t) => t.episodeId,
          referencedTable: $db.bangumiEpisodeCollections,
          getReferencedColumn: (t) => t.episodeId,
          builder:
              (
                joinBuilder, {
                $addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer,
              }) => $$BangumiEpisodeCollectionsTableAnnotationComposer(
                $db: $db,
                $table: $db.bangumiEpisodeCollections,
                $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
                joinBuilder: joinBuilder,
                $removeJoinBuilderFromRootComposer:
                    $removeJoinBuilderFromRootComposer,
              ),
        );
    return f(composer);
  }

  Expression<T> bangumiSyncOperationsRefs<T extends Object>(
    Expression<T> Function($$BangumiSyncOperationsTableAnnotationComposer a) f,
  ) {
    final $$BangumiSyncOperationsTableAnnotationComposer composer =
        $composerBuilder(
          composer: this,
          getCurrentColumn: (t) => t.episodeId,
          referencedTable: $db.bangumiSyncOperations,
          getReferencedColumn: (t) => t.episodeId,
          builder:
              (
                joinBuilder, {
                $addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer,
              }) => $$BangumiSyncOperationsTableAnnotationComposer(
                $db: $db,
                $table: $db.bangumiSyncOperations,
                $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
                joinBuilder: joinBuilder,
                $removeJoinBuilderFromRootComposer:
                    $removeJoinBuilderFromRootComposer,
              ),
        );
    return f(composer);
  }
}

class $$BangumiEpisodesTableTableManager
    extends
        RootTableManager<
          _$WynimeDatabase,
          $BangumiEpisodesTable,
          BangumiEpisodeRecord,
          $$BangumiEpisodesTableFilterComposer,
          $$BangumiEpisodesTableOrderingComposer,
          $$BangumiEpisodesTableAnnotationComposer,
          $$BangumiEpisodesTableCreateCompanionBuilder,
          $$BangumiEpisodesTableUpdateCompanionBuilder,
          (BangumiEpisodeRecord, $$BangumiEpisodesTableReferences),
          BangumiEpisodeRecord,
          PrefetchHooks Function({
            bool subjectId,
            bool bangumiEpisodeCollectionsRefs,
            bool bangumiSyncOperationsRefs,
          })
        > {
  $$BangumiEpisodesTableTableManager(
    _$WynimeDatabase db,
    $BangumiEpisodesTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$BangumiEpisodesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$BangumiEpisodesTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$BangumiEpisodesTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> episodeId = const Value.absent(),
                Value<String> subjectId = const Value.absent(),
                Value<String> name = const Value.absent(),
                Value<String> nameCn = const Value.absent(),
                Value<double> sort = const Value.absent(),
                Value<int> type = const Value.absent(),
                Value<int?> duration = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => BangumiEpisodesCompanion(
                episodeId: episodeId,
                subjectId: subjectId,
                name: name,
                nameCn: nameCn,
                sort: sort,
                type: type,
                duration: duration,
                updatedAt: updatedAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String episodeId,
                required String subjectId,
                required String name,
                required String nameCn,
                required double sort,
                required int type,
                Value<int?> duration = const Value.absent(),
                required DateTime updatedAt,
                Value<int> rowid = const Value.absent(),
              }) => BangumiEpisodesCompanion.insert(
                episodeId: episodeId,
                subjectId: subjectId,
                name: name,
                nameCn: nameCn,
                sort: sort,
                type: type,
                duration: duration,
                updatedAt: updatedAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable(table),
                  $$BangumiEpisodesTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback:
              ({
                subjectId = false,
                bangumiEpisodeCollectionsRefs = false,
                bangumiSyncOperationsRefs = false,
              }) {
                return PrefetchHooks(
                  db: db,
                  explicitlyWatchedTables: [
                    if (bangumiEpisodeCollectionsRefs)
                      db.bangumiEpisodeCollections,
                    if (bangumiSyncOperationsRefs) db.bangumiSyncOperations,
                  ],
                  addJoins:
                      <
                        T extends TableManagerState<
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic
                        >
                      >(state) {
                        if (subjectId) {
                          state =
                              state.withJoin(
                                    currentTable: table,
                                    currentColumn: table.subjectId,
                                    referencedTable:
                                        $$BangumiEpisodesTableReferences
                                            ._subjectIdTable(db),
                                    referencedColumn:
                                        $$BangumiEpisodesTableReferences
                                            ._subjectIdTable(db)
                                            .subjectId,
                                  )
                                  as T;
                        }

                        return state;
                      },
                  getPrefetchedDataCallback: (items) async {
                    return [
                      if (bangumiEpisodeCollectionsRefs)
                        await $_getPrefetchedData<
                          BangumiEpisodeRecord,
                          $BangumiEpisodesTable,
                          BangumiEpisodeCollectionRecord
                        >(
                          currentTable: table,
                          referencedTable: $$BangumiEpisodesTableReferences
                              ._bangumiEpisodeCollectionsRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$BangumiEpisodesTableReferences(
                                db,
                                table,
                                p0,
                              ).bangumiEpisodeCollectionsRefs,
                          referencedItemsForCurrentItem:
                              (item, referencedItems) => referencedItems.where(
                                (e) => e.episodeId == item.episodeId,
                              ),
                          typedResults: items,
                        ),
                      if (bangumiSyncOperationsRefs)
                        await $_getPrefetchedData<
                          BangumiEpisodeRecord,
                          $BangumiEpisodesTable,
                          BangumiSyncOperationRecord
                        >(
                          currentTable: table,
                          referencedTable: $$BangumiEpisodesTableReferences
                              ._bangumiSyncOperationsRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$BangumiEpisodesTableReferences(
                                db,
                                table,
                                p0,
                              ).bangumiSyncOperationsRefs,
                          referencedItemsForCurrentItem:
                              (item, referencedItems) => referencedItems.where(
                                (e) => e.episodeId == item.episodeId,
                              ),
                          typedResults: items,
                        ),
                    ];
                  },
                );
              },
        ),
      );
}

typedef $$BangumiEpisodesTableProcessedTableManager =
    ProcessedTableManager<
      _$WynimeDatabase,
      $BangumiEpisodesTable,
      BangumiEpisodeRecord,
      $$BangumiEpisodesTableFilterComposer,
      $$BangumiEpisodesTableOrderingComposer,
      $$BangumiEpisodesTableAnnotationComposer,
      $$BangumiEpisodesTableCreateCompanionBuilder,
      $$BangumiEpisodesTableUpdateCompanionBuilder,
      (BangumiEpisodeRecord, $$BangumiEpisodesTableReferences),
      BangumiEpisodeRecord,
      PrefetchHooks Function({
        bool subjectId,
        bool bangumiEpisodeCollectionsRefs,
        bool bangumiSyncOperationsRefs,
      })
    >;
typedef $$BangumiEpisodeCollectionsTableCreateCompanionBuilder =
    BangumiEpisodeCollectionsCompanion Function({
      required String accountId,
      required String episodeId,
      Value<bool> watched,
      Value<String?> remoteRevision,
      required DateTime localUpdatedAt,
      Value<DateTime?> remoteUpdatedAt,
      Value<int> rowid,
    });
typedef $$BangumiEpisodeCollectionsTableUpdateCompanionBuilder =
    BangumiEpisodeCollectionsCompanion Function({
      Value<String> accountId,
      Value<String> episodeId,
      Value<bool> watched,
      Value<String?> remoteRevision,
      Value<DateTime> localUpdatedAt,
      Value<DateTime?> remoteUpdatedAt,
      Value<int> rowid,
    });

final class $$BangumiEpisodeCollectionsTableReferences
    extends
        BaseReferences<
          _$WynimeDatabase,
          $BangumiEpisodeCollectionsTable,
          BangumiEpisodeCollectionRecord
        > {
  $$BangumiEpisodeCollectionsTableReferences(
    super.$_db,
    super.$_table,
    super.$_typedResult,
  );

  static $BangumiAccountsTable _accountIdTable(_$WynimeDatabase db) =>
      db.bangumiAccounts.createAlias(
        'bangumi_episode_collections__account_id__bangumi_accounts__account_id',
      );

  $$BangumiAccountsTableProcessedTableManager get accountId {
    final $_column = $_itemColumn<String>('account_id')!;

    final manager = $$BangumiAccountsTableTableManager(
      $_db,
      $_db.bangumiAccounts,
    ).filter((f) => f.accountId.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_accountIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }

  static $BangumiEpisodesTable _episodeIdTable(_$WynimeDatabase db) =>
      db.bangumiEpisodes.createAlias(
        'bangumi_episode_collections__episode_id__bangumi_episodes__episode_id',
      );

  $$BangumiEpisodesTableProcessedTableManager get episodeId {
    final $_column = $_itemColumn<String>('episode_id')!;

    final manager = $$BangumiEpisodesTableTableManager(
      $_db,
      $_db.bangumiEpisodes,
    ).filter((f) => f.episodeId.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_episodeIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }
}

class $$BangumiEpisodeCollectionsTableFilterComposer
    extends Composer<_$WynimeDatabase, $BangumiEpisodeCollectionsTable> {
  $$BangumiEpisodeCollectionsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<bool> get watched => $composableBuilder(
    column: $table.watched,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get remoteRevision => $composableBuilder(
    column: $table.remoteRevision,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get localUpdatedAt => $composableBuilder(
    column: $table.localUpdatedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get remoteUpdatedAt => $composableBuilder(
    column: $table.remoteUpdatedAt,
    builder: (column) => ColumnFilters(column),
  );

  $$BangumiAccountsTableFilterComposer get accountId {
    final $$BangumiAccountsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.accountId,
      referencedTable: $db.bangumiAccounts,
      getReferencedColumn: (t) => t.accountId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiAccountsTableFilterComposer(
            $db: $db,
            $table: $db.bangumiAccounts,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$BangumiEpisodesTableFilterComposer get episodeId {
    final $$BangumiEpisodesTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.episodeId,
      referencedTable: $db.bangumiEpisodes,
      getReferencedColumn: (t) => t.episodeId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiEpisodesTableFilterComposer(
            $db: $db,
            $table: $db.bangumiEpisodes,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$BangumiEpisodeCollectionsTableOrderingComposer
    extends Composer<_$WynimeDatabase, $BangumiEpisodeCollectionsTable> {
  $$BangumiEpisodeCollectionsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<bool> get watched => $composableBuilder(
    column: $table.watched,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get remoteRevision => $composableBuilder(
    column: $table.remoteRevision,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get localUpdatedAt => $composableBuilder(
    column: $table.localUpdatedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get remoteUpdatedAt => $composableBuilder(
    column: $table.remoteUpdatedAt,
    builder: (column) => ColumnOrderings(column),
  );

  $$BangumiAccountsTableOrderingComposer get accountId {
    final $$BangumiAccountsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.accountId,
      referencedTable: $db.bangumiAccounts,
      getReferencedColumn: (t) => t.accountId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiAccountsTableOrderingComposer(
            $db: $db,
            $table: $db.bangumiAccounts,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$BangumiEpisodesTableOrderingComposer get episodeId {
    final $$BangumiEpisodesTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.episodeId,
      referencedTable: $db.bangumiEpisodes,
      getReferencedColumn: (t) => t.episodeId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiEpisodesTableOrderingComposer(
            $db: $db,
            $table: $db.bangumiEpisodes,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$BangumiEpisodeCollectionsTableAnnotationComposer
    extends Composer<_$WynimeDatabase, $BangumiEpisodeCollectionsTable> {
  $$BangumiEpisodeCollectionsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<bool> get watched =>
      $composableBuilder(column: $table.watched, builder: (column) => column);

  GeneratedColumn<String> get remoteRevision => $composableBuilder(
    column: $table.remoteRevision,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get localUpdatedAt => $composableBuilder(
    column: $table.localUpdatedAt,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get remoteUpdatedAt => $composableBuilder(
    column: $table.remoteUpdatedAt,
    builder: (column) => column,
  );

  $$BangumiAccountsTableAnnotationComposer get accountId {
    final $$BangumiAccountsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.accountId,
      referencedTable: $db.bangumiAccounts,
      getReferencedColumn: (t) => t.accountId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiAccountsTableAnnotationComposer(
            $db: $db,
            $table: $db.bangumiAccounts,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$BangumiEpisodesTableAnnotationComposer get episodeId {
    final $$BangumiEpisodesTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.episodeId,
      referencedTable: $db.bangumiEpisodes,
      getReferencedColumn: (t) => t.episodeId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiEpisodesTableAnnotationComposer(
            $db: $db,
            $table: $db.bangumiEpisodes,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$BangumiEpisodeCollectionsTableTableManager
    extends
        RootTableManager<
          _$WynimeDatabase,
          $BangumiEpisodeCollectionsTable,
          BangumiEpisodeCollectionRecord,
          $$BangumiEpisodeCollectionsTableFilterComposer,
          $$BangumiEpisodeCollectionsTableOrderingComposer,
          $$BangumiEpisodeCollectionsTableAnnotationComposer,
          $$BangumiEpisodeCollectionsTableCreateCompanionBuilder,
          $$BangumiEpisodeCollectionsTableUpdateCompanionBuilder,
          (
            BangumiEpisodeCollectionRecord,
            $$BangumiEpisodeCollectionsTableReferences,
          ),
          BangumiEpisodeCollectionRecord,
          PrefetchHooks Function({bool accountId, bool episodeId})
        > {
  $$BangumiEpisodeCollectionsTableTableManager(
    _$WynimeDatabase db,
    $BangumiEpisodeCollectionsTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$BangumiEpisodeCollectionsTableFilterComposer(
                $db: db,
                $table: table,
              ),
          createOrderingComposer: () =>
              $$BangumiEpisodeCollectionsTableOrderingComposer(
                $db: db,
                $table: table,
              ),
          createComputedFieldComposer: () =>
              $$BangumiEpisodeCollectionsTableAnnotationComposer(
                $db: db,
                $table: table,
              ),
          updateCompanionCallback:
              ({
                Value<String> accountId = const Value.absent(),
                Value<String> episodeId = const Value.absent(),
                Value<bool> watched = const Value.absent(),
                Value<String?> remoteRevision = const Value.absent(),
                Value<DateTime> localUpdatedAt = const Value.absent(),
                Value<DateTime?> remoteUpdatedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => BangumiEpisodeCollectionsCompanion(
                accountId: accountId,
                episodeId: episodeId,
                watched: watched,
                remoteRevision: remoteRevision,
                localUpdatedAt: localUpdatedAt,
                remoteUpdatedAt: remoteUpdatedAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String accountId,
                required String episodeId,
                Value<bool> watched = const Value.absent(),
                Value<String?> remoteRevision = const Value.absent(),
                required DateTime localUpdatedAt,
                Value<DateTime?> remoteUpdatedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => BangumiEpisodeCollectionsCompanion.insert(
                accountId: accountId,
                episodeId: episodeId,
                watched: watched,
                remoteRevision: remoteRevision,
                localUpdatedAt: localUpdatedAt,
                remoteUpdatedAt: remoteUpdatedAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable(table),
                  $$BangumiEpisodeCollectionsTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({accountId = false, episodeId = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [],
              addJoins:
                  <
                    T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic
                    >
                  >(state) {
                    if (accountId) {
                      state =
                          state.withJoin(
                                currentTable: table,
                                currentColumn: table.accountId,
                                referencedTable:
                                    $$BangumiEpisodeCollectionsTableReferences
                                        ._accountIdTable(db),
                                referencedColumn:
                                    $$BangumiEpisodeCollectionsTableReferences
                                        ._accountIdTable(db)
                                        .accountId,
                              )
                              as T;
                    }
                    if (episodeId) {
                      state =
                          state.withJoin(
                                currentTable: table,
                                currentColumn: table.episodeId,
                                referencedTable:
                                    $$BangumiEpisodeCollectionsTableReferences
                                        ._episodeIdTable(db),
                                referencedColumn:
                                    $$BangumiEpisodeCollectionsTableReferences
                                        ._episodeIdTable(db)
                                        .episodeId,
                              )
                              as T;
                    }

                    return state;
                  },
              getPrefetchedDataCallback: (items) async {
                return [];
              },
            );
          },
        ),
      );
}

typedef $$BangumiEpisodeCollectionsTableProcessedTableManager =
    ProcessedTableManager<
      _$WynimeDatabase,
      $BangumiEpisodeCollectionsTable,
      BangumiEpisodeCollectionRecord,
      $$BangumiEpisodeCollectionsTableFilterComposer,
      $$BangumiEpisodeCollectionsTableOrderingComposer,
      $$BangumiEpisodeCollectionsTableAnnotationComposer,
      $$BangumiEpisodeCollectionsTableCreateCompanionBuilder,
      $$BangumiEpisodeCollectionsTableUpdateCompanionBuilder,
      (
        BangumiEpisodeCollectionRecord,
        $$BangumiEpisodeCollectionsTableReferences,
      ),
      BangumiEpisodeCollectionRecord,
      PrefetchHooks Function({bool accountId, bool episodeId})
    >;
typedef $$BangumiMappingsTableCreateCompanionBuilder =
    BangumiMappingsCompanion Function({
      required String localSubjectKey,
      required String bangumiSubjectId,
      required DateTime confirmedAt,
      Value<int> rowid,
    });
typedef $$BangumiMappingsTableUpdateCompanionBuilder =
    BangumiMappingsCompanion Function({
      Value<String> localSubjectKey,
      Value<String> bangumiSubjectId,
      Value<DateTime> confirmedAt,
      Value<int> rowid,
    });

final class $$BangumiMappingsTableReferences
    extends
        BaseReferences<
          _$WynimeDatabase,
          $BangumiMappingsTable,
          BangumiMappingRecord
        > {
  $$BangumiMappingsTableReferences(
    super.$_db,
    super.$_table,
    super.$_typedResult,
  );

  static $BangumiSubjectsTable _bangumiSubjectIdTable(_$WynimeDatabase db) =>
      db.bangumiSubjects.createAlias(
        'bangumi_mappings__bangumi_subject_id__bangumi_subjects__subject_id',
      );

  $$BangumiSubjectsTableProcessedTableManager get bangumiSubjectId {
    final $_column = $_itemColumn<String>('bangumi_subject_id')!;

    final manager = $$BangumiSubjectsTableTableManager(
      $_db,
      $_db.bangumiSubjects,
    ).filter((f) => f.subjectId.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_bangumiSubjectIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }
}

class $$BangumiMappingsTableFilterComposer
    extends Composer<_$WynimeDatabase, $BangumiMappingsTable> {
  $$BangumiMappingsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get localSubjectKey => $composableBuilder(
    column: $table.localSubjectKey,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get confirmedAt => $composableBuilder(
    column: $table.confirmedAt,
    builder: (column) => ColumnFilters(column),
  );

  $$BangumiSubjectsTableFilterComposer get bangumiSubjectId {
    final $$BangumiSubjectsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.bangumiSubjectId,
      referencedTable: $db.bangumiSubjects,
      getReferencedColumn: (t) => t.subjectId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiSubjectsTableFilterComposer(
            $db: $db,
            $table: $db.bangumiSubjects,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$BangumiMappingsTableOrderingComposer
    extends Composer<_$WynimeDatabase, $BangumiMappingsTable> {
  $$BangumiMappingsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get localSubjectKey => $composableBuilder(
    column: $table.localSubjectKey,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get confirmedAt => $composableBuilder(
    column: $table.confirmedAt,
    builder: (column) => ColumnOrderings(column),
  );

  $$BangumiSubjectsTableOrderingComposer get bangumiSubjectId {
    final $$BangumiSubjectsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.bangumiSubjectId,
      referencedTable: $db.bangumiSubjects,
      getReferencedColumn: (t) => t.subjectId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiSubjectsTableOrderingComposer(
            $db: $db,
            $table: $db.bangumiSubjects,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$BangumiMappingsTableAnnotationComposer
    extends Composer<_$WynimeDatabase, $BangumiMappingsTable> {
  $$BangumiMappingsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get localSubjectKey => $composableBuilder(
    column: $table.localSubjectKey,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get confirmedAt => $composableBuilder(
    column: $table.confirmedAt,
    builder: (column) => column,
  );

  $$BangumiSubjectsTableAnnotationComposer get bangumiSubjectId {
    final $$BangumiSubjectsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.bangumiSubjectId,
      referencedTable: $db.bangumiSubjects,
      getReferencedColumn: (t) => t.subjectId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiSubjectsTableAnnotationComposer(
            $db: $db,
            $table: $db.bangumiSubjects,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$BangumiMappingsTableTableManager
    extends
        RootTableManager<
          _$WynimeDatabase,
          $BangumiMappingsTable,
          BangumiMappingRecord,
          $$BangumiMappingsTableFilterComposer,
          $$BangumiMappingsTableOrderingComposer,
          $$BangumiMappingsTableAnnotationComposer,
          $$BangumiMappingsTableCreateCompanionBuilder,
          $$BangumiMappingsTableUpdateCompanionBuilder,
          (BangumiMappingRecord, $$BangumiMappingsTableReferences),
          BangumiMappingRecord,
          PrefetchHooks Function({bool bangumiSubjectId})
        > {
  $$BangumiMappingsTableTableManager(
    _$WynimeDatabase db,
    $BangumiMappingsTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$BangumiMappingsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$BangumiMappingsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$BangumiMappingsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> localSubjectKey = const Value.absent(),
                Value<String> bangumiSubjectId = const Value.absent(),
                Value<DateTime> confirmedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => BangumiMappingsCompanion(
                localSubjectKey: localSubjectKey,
                bangumiSubjectId: bangumiSubjectId,
                confirmedAt: confirmedAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String localSubjectKey,
                required String bangumiSubjectId,
                required DateTime confirmedAt,
                Value<int> rowid = const Value.absent(),
              }) => BangumiMappingsCompanion.insert(
                localSubjectKey: localSubjectKey,
                bangumiSubjectId: bangumiSubjectId,
                confirmedAt: confirmedAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable(table),
                  $$BangumiMappingsTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({bangumiSubjectId = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [],
              addJoins:
                  <
                    T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic
                    >
                  >(state) {
                    if (bangumiSubjectId) {
                      state =
                          state.withJoin(
                                currentTable: table,
                                currentColumn: table.bangumiSubjectId,
                                referencedTable:
                                    $$BangumiMappingsTableReferences
                                        ._bangumiSubjectIdTable(db),
                                referencedColumn:
                                    $$BangumiMappingsTableReferences
                                        ._bangumiSubjectIdTable(db)
                                        .subjectId,
                              )
                              as T;
                    }

                    return state;
                  },
              getPrefetchedDataCallback: (items) async {
                return [];
              },
            );
          },
        ),
      );
}

typedef $$BangumiMappingsTableProcessedTableManager =
    ProcessedTableManager<
      _$WynimeDatabase,
      $BangumiMappingsTable,
      BangumiMappingRecord,
      $$BangumiMappingsTableFilterComposer,
      $$BangumiMappingsTableOrderingComposer,
      $$BangumiMappingsTableAnnotationComposer,
      $$BangumiMappingsTableCreateCompanionBuilder,
      $$BangumiMappingsTableUpdateCompanionBuilder,
      (BangumiMappingRecord, $$BangumiMappingsTableReferences),
      BangumiMappingRecord,
      PrefetchHooks Function({bool bangumiSubjectId})
    >;
typedef $$BangumiSyncOperationsTableCreateCompanionBuilder =
    BangumiSyncOperationsCompanion Function({
      required String operationId,
      required String accountId,
      required String subjectId,
      Value<String?> episodeId,
      required String kind,
      Value<int?> collectionStatus,
      Value<bool?> watched,
      Value<String?> baseRemoteRevision,
      Value<int?> baseCollectionStatus,
      Value<bool?> baseWatched,
      required String state,
      Value<int> attempts,
      Value<DateTime?> nextAttemptAt,
      Value<String?> lastErrorCode,
      Value<int?> statusCode,
      required DateTime createdAt,
      required DateTime updatedAt,
      Value<int> rowid,
    });
typedef $$BangumiSyncOperationsTableUpdateCompanionBuilder =
    BangumiSyncOperationsCompanion Function({
      Value<String> operationId,
      Value<String> accountId,
      Value<String> subjectId,
      Value<String?> episodeId,
      Value<String> kind,
      Value<int?> collectionStatus,
      Value<bool?> watched,
      Value<String?> baseRemoteRevision,
      Value<int?> baseCollectionStatus,
      Value<bool?> baseWatched,
      Value<String> state,
      Value<int> attempts,
      Value<DateTime?> nextAttemptAt,
      Value<String?> lastErrorCode,
      Value<int?> statusCode,
      Value<DateTime> createdAt,
      Value<DateTime> updatedAt,
      Value<int> rowid,
    });

final class $$BangumiSyncOperationsTableReferences
    extends
        BaseReferences<
          _$WynimeDatabase,
          $BangumiSyncOperationsTable,
          BangumiSyncOperationRecord
        > {
  $$BangumiSyncOperationsTableReferences(
    super.$_db,
    super.$_table,
    super.$_typedResult,
  );

  static $BangumiAccountsTable _accountIdTable(_$WynimeDatabase db) =>
      db.bangumiAccounts.createAlias(
        'bangumi_sync_operations__account_id__bangumi_accounts__account_id',
      );

  $$BangumiAccountsTableProcessedTableManager get accountId {
    final $_column = $_itemColumn<String>('account_id')!;

    final manager = $$BangumiAccountsTableTableManager(
      $_db,
      $_db.bangumiAccounts,
    ).filter((f) => f.accountId.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_accountIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }

  static $BangumiSubjectsTable _subjectIdTable(_$WynimeDatabase db) =>
      db.bangumiSubjects.createAlias(
        'bangumi_sync_operations__subject_id__bangumi_subjects__subject_id',
      );

  $$BangumiSubjectsTableProcessedTableManager get subjectId {
    final $_column = $_itemColumn<String>('subject_id')!;

    final manager = $$BangumiSubjectsTableTableManager(
      $_db,
      $_db.bangumiSubjects,
    ).filter((f) => f.subjectId.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_subjectIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }

  static $BangumiEpisodesTable _episodeIdTable(_$WynimeDatabase db) =>
      db.bangumiEpisodes.createAlias(
        'bangumi_sync_operations__episode_id__bangumi_episodes__episode_id',
      );

  $$BangumiEpisodesTableProcessedTableManager? get episodeId {
    final $_column = $_itemColumn<String>('episode_id');
    if ($_column == null) return null;
    final manager = $$BangumiEpisodesTableTableManager(
      $_db,
      $_db.bangumiEpisodes,
    ).filter((f) => f.episodeId.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_episodeIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }
}

class $$BangumiSyncOperationsTableFilterComposer
    extends Composer<_$WynimeDatabase, $BangumiSyncOperationsTable> {
  $$BangumiSyncOperationsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get operationId => $composableBuilder(
    column: $table.operationId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get collectionStatus => $composableBuilder(
    column: $table.collectionStatus,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get watched => $composableBuilder(
    column: $table.watched,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get baseRemoteRevision => $composableBuilder(
    column: $table.baseRemoteRevision,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get baseCollectionStatus => $composableBuilder(
    column: $table.baseCollectionStatus,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get baseWatched => $composableBuilder(
    column: $table.baseWatched,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get state => $composableBuilder(
    column: $table.state,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get attempts => $composableBuilder(
    column: $table.attempts,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get nextAttemptAt => $composableBuilder(
    column: $table.nextAttemptAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get lastErrorCode => $composableBuilder(
    column: $table.lastErrorCode,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get statusCode => $composableBuilder(
    column: $table.statusCode,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnFilters(column),
  );

  $$BangumiAccountsTableFilterComposer get accountId {
    final $$BangumiAccountsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.accountId,
      referencedTable: $db.bangumiAccounts,
      getReferencedColumn: (t) => t.accountId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiAccountsTableFilterComposer(
            $db: $db,
            $table: $db.bangumiAccounts,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$BangumiSubjectsTableFilterComposer get subjectId {
    final $$BangumiSubjectsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.subjectId,
      referencedTable: $db.bangumiSubjects,
      getReferencedColumn: (t) => t.subjectId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiSubjectsTableFilterComposer(
            $db: $db,
            $table: $db.bangumiSubjects,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$BangumiEpisodesTableFilterComposer get episodeId {
    final $$BangumiEpisodesTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.episodeId,
      referencedTable: $db.bangumiEpisodes,
      getReferencedColumn: (t) => t.episodeId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiEpisodesTableFilterComposer(
            $db: $db,
            $table: $db.bangumiEpisodes,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$BangumiSyncOperationsTableOrderingComposer
    extends Composer<_$WynimeDatabase, $BangumiSyncOperationsTable> {
  $$BangumiSyncOperationsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get operationId => $composableBuilder(
    column: $table.operationId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get collectionStatus => $composableBuilder(
    column: $table.collectionStatus,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get watched => $composableBuilder(
    column: $table.watched,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get baseRemoteRevision => $composableBuilder(
    column: $table.baseRemoteRevision,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get baseCollectionStatus => $composableBuilder(
    column: $table.baseCollectionStatus,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get baseWatched => $composableBuilder(
    column: $table.baseWatched,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get state => $composableBuilder(
    column: $table.state,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get attempts => $composableBuilder(
    column: $table.attempts,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get nextAttemptAt => $composableBuilder(
    column: $table.nextAttemptAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get lastErrorCode => $composableBuilder(
    column: $table.lastErrorCode,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get statusCode => $composableBuilder(
    column: $table.statusCode,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnOrderings(column),
  );

  $$BangumiAccountsTableOrderingComposer get accountId {
    final $$BangumiAccountsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.accountId,
      referencedTable: $db.bangumiAccounts,
      getReferencedColumn: (t) => t.accountId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiAccountsTableOrderingComposer(
            $db: $db,
            $table: $db.bangumiAccounts,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$BangumiSubjectsTableOrderingComposer get subjectId {
    final $$BangumiSubjectsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.subjectId,
      referencedTable: $db.bangumiSubjects,
      getReferencedColumn: (t) => t.subjectId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiSubjectsTableOrderingComposer(
            $db: $db,
            $table: $db.bangumiSubjects,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$BangumiEpisodesTableOrderingComposer get episodeId {
    final $$BangumiEpisodesTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.episodeId,
      referencedTable: $db.bangumiEpisodes,
      getReferencedColumn: (t) => t.episodeId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiEpisodesTableOrderingComposer(
            $db: $db,
            $table: $db.bangumiEpisodes,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$BangumiSyncOperationsTableAnnotationComposer
    extends Composer<_$WynimeDatabase, $BangumiSyncOperationsTable> {
  $$BangumiSyncOperationsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get operationId => $composableBuilder(
    column: $table.operationId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get kind =>
      $composableBuilder(column: $table.kind, builder: (column) => column);

  GeneratedColumn<int> get collectionStatus => $composableBuilder(
    column: $table.collectionStatus,
    builder: (column) => column,
  );

  GeneratedColumn<bool> get watched =>
      $composableBuilder(column: $table.watched, builder: (column) => column);

  GeneratedColumn<String> get baseRemoteRevision => $composableBuilder(
    column: $table.baseRemoteRevision,
    builder: (column) => column,
  );

  GeneratedColumn<int> get baseCollectionStatus => $composableBuilder(
    column: $table.baseCollectionStatus,
    builder: (column) => column,
  );

  GeneratedColumn<bool> get baseWatched => $composableBuilder(
    column: $table.baseWatched,
    builder: (column) => column,
  );

  GeneratedColumn<String> get state =>
      $composableBuilder(column: $table.state, builder: (column) => column);

  GeneratedColumn<int> get attempts =>
      $composableBuilder(column: $table.attempts, builder: (column) => column);

  GeneratedColumn<DateTime> get nextAttemptAt => $composableBuilder(
    column: $table.nextAttemptAt,
    builder: (column) => column,
  );

  GeneratedColumn<String> get lastErrorCode => $composableBuilder(
    column: $table.lastErrorCode,
    builder: (column) => column,
  );

  GeneratedColumn<int> get statusCode => $composableBuilder(
    column: $table.statusCode,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);

  $$BangumiAccountsTableAnnotationComposer get accountId {
    final $$BangumiAccountsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.accountId,
      referencedTable: $db.bangumiAccounts,
      getReferencedColumn: (t) => t.accountId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiAccountsTableAnnotationComposer(
            $db: $db,
            $table: $db.bangumiAccounts,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$BangumiSubjectsTableAnnotationComposer get subjectId {
    final $$BangumiSubjectsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.subjectId,
      referencedTable: $db.bangumiSubjects,
      getReferencedColumn: (t) => t.subjectId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiSubjectsTableAnnotationComposer(
            $db: $db,
            $table: $db.bangumiSubjects,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$BangumiEpisodesTableAnnotationComposer get episodeId {
    final $$BangumiEpisodesTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.episodeId,
      referencedTable: $db.bangumiEpisodes,
      getReferencedColumn: (t) => t.episodeId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiEpisodesTableAnnotationComposer(
            $db: $db,
            $table: $db.bangumiEpisodes,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$BangumiSyncOperationsTableTableManager
    extends
        RootTableManager<
          _$WynimeDatabase,
          $BangumiSyncOperationsTable,
          BangumiSyncOperationRecord,
          $$BangumiSyncOperationsTableFilterComposer,
          $$BangumiSyncOperationsTableOrderingComposer,
          $$BangumiSyncOperationsTableAnnotationComposer,
          $$BangumiSyncOperationsTableCreateCompanionBuilder,
          $$BangumiSyncOperationsTableUpdateCompanionBuilder,
          (BangumiSyncOperationRecord, $$BangumiSyncOperationsTableReferences),
          BangumiSyncOperationRecord,
          PrefetchHooks Function({
            bool accountId,
            bool subjectId,
            bool episodeId,
          })
        > {
  $$BangumiSyncOperationsTableTableManager(
    _$WynimeDatabase db,
    $BangumiSyncOperationsTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$BangumiSyncOperationsTableFilterComposer(
                $db: db,
                $table: table,
              ),
          createOrderingComposer: () =>
              $$BangumiSyncOperationsTableOrderingComposer(
                $db: db,
                $table: table,
              ),
          createComputedFieldComposer: () =>
              $$BangumiSyncOperationsTableAnnotationComposer(
                $db: db,
                $table: table,
              ),
          updateCompanionCallback:
              ({
                Value<String> operationId = const Value.absent(),
                Value<String> accountId = const Value.absent(),
                Value<String> subjectId = const Value.absent(),
                Value<String?> episodeId = const Value.absent(),
                Value<String> kind = const Value.absent(),
                Value<int?> collectionStatus = const Value.absent(),
                Value<bool?> watched = const Value.absent(),
                Value<String?> baseRemoteRevision = const Value.absent(),
                Value<int?> baseCollectionStatus = const Value.absent(),
                Value<bool?> baseWatched = const Value.absent(),
                Value<String> state = const Value.absent(),
                Value<int> attempts = const Value.absent(),
                Value<DateTime?> nextAttemptAt = const Value.absent(),
                Value<String?> lastErrorCode = const Value.absent(),
                Value<int?> statusCode = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => BangumiSyncOperationsCompanion(
                operationId: operationId,
                accountId: accountId,
                subjectId: subjectId,
                episodeId: episodeId,
                kind: kind,
                collectionStatus: collectionStatus,
                watched: watched,
                baseRemoteRevision: baseRemoteRevision,
                baseCollectionStatus: baseCollectionStatus,
                baseWatched: baseWatched,
                state: state,
                attempts: attempts,
                nextAttemptAt: nextAttemptAt,
                lastErrorCode: lastErrorCode,
                statusCode: statusCode,
                createdAt: createdAt,
                updatedAt: updatedAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String operationId,
                required String accountId,
                required String subjectId,
                Value<String?> episodeId = const Value.absent(),
                required String kind,
                Value<int?> collectionStatus = const Value.absent(),
                Value<bool?> watched = const Value.absent(),
                Value<String?> baseRemoteRevision = const Value.absent(),
                Value<int?> baseCollectionStatus = const Value.absent(),
                Value<bool?> baseWatched = const Value.absent(),
                required String state,
                Value<int> attempts = const Value.absent(),
                Value<DateTime?> nextAttemptAt = const Value.absent(),
                Value<String?> lastErrorCode = const Value.absent(),
                Value<int?> statusCode = const Value.absent(),
                required DateTime createdAt,
                required DateTime updatedAt,
                Value<int> rowid = const Value.absent(),
              }) => BangumiSyncOperationsCompanion.insert(
                operationId: operationId,
                accountId: accountId,
                subjectId: subjectId,
                episodeId: episodeId,
                kind: kind,
                collectionStatus: collectionStatus,
                watched: watched,
                baseRemoteRevision: baseRemoteRevision,
                baseCollectionStatus: baseCollectionStatus,
                baseWatched: baseWatched,
                state: state,
                attempts: attempts,
                nextAttemptAt: nextAttemptAt,
                lastErrorCode: lastErrorCode,
                statusCode: statusCode,
                createdAt: createdAt,
                updatedAt: updatedAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable(table),
                  $$BangumiSyncOperationsTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback:
              ({accountId = false, subjectId = false, episodeId = false}) {
                return PrefetchHooks(
                  db: db,
                  explicitlyWatchedTables: [],
                  addJoins:
                      <
                        T extends TableManagerState<
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic
                        >
                      >(state) {
                        if (accountId) {
                          state =
                              state.withJoin(
                                    currentTable: table,
                                    currentColumn: table.accountId,
                                    referencedTable:
                                        $$BangumiSyncOperationsTableReferences
                                            ._accountIdTable(db),
                                    referencedColumn:
                                        $$BangumiSyncOperationsTableReferences
                                            ._accountIdTable(db)
                                            .accountId,
                                  )
                                  as T;
                        }
                        if (subjectId) {
                          state =
                              state.withJoin(
                                    currentTable: table,
                                    currentColumn: table.subjectId,
                                    referencedTable:
                                        $$BangumiSyncOperationsTableReferences
                                            ._subjectIdTable(db),
                                    referencedColumn:
                                        $$BangumiSyncOperationsTableReferences
                                            ._subjectIdTable(db)
                                            .subjectId,
                                  )
                                  as T;
                        }
                        if (episodeId) {
                          state =
                              state.withJoin(
                                    currentTable: table,
                                    currentColumn: table.episodeId,
                                    referencedTable:
                                        $$BangumiSyncOperationsTableReferences
                                            ._episodeIdTable(db),
                                    referencedColumn:
                                        $$BangumiSyncOperationsTableReferences
                                            ._episodeIdTable(db)
                                            .episodeId,
                                  )
                                  as T;
                        }

                        return state;
                      },
                  getPrefetchedDataCallback: (items) async {
                    return [];
                  },
                );
              },
        ),
      );
}

typedef $$BangumiSyncOperationsTableProcessedTableManager =
    ProcessedTableManager<
      _$WynimeDatabase,
      $BangumiSyncOperationsTable,
      BangumiSyncOperationRecord,
      $$BangumiSyncOperationsTableFilterComposer,
      $$BangumiSyncOperationsTableOrderingComposer,
      $$BangumiSyncOperationsTableAnnotationComposer,
      $$BangumiSyncOperationsTableCreateCompanionBuilder,
      $$BangumiSyncOperationsTableUpdateCompanionBuilder,
      (BangumiSyncOperationRecord, $$BangumiSyncOperationsTableReferences),
      BangumiSyncOperationRecord,
      PrefetchHooks Function({bool accountId, bool subjectId, bool episodeId})
    >;
typedef $$BangumiConflictSnapshotsTableCreateCompanionBuilder =
    BangumiConflictSnapshotsCompanion Function({
      required String operationId,
      required String accountId,
      required String subjectId,
      Value<int?> status,
      required String watchedEpisodeIds,
      required String remoteRevision,
      required DateTime capturedAt,
      Value<int> rowid,
    });
typedef $$BangumiConflictSnapshotsTableUpdateCompanionBuilder =
    BangumiConflictSnapshotsCompanion Function({
      Value<String> operationId,
      Value<String> accountId,
      Value<String> subjectId,
      Value<int?> status,
      Value<String> watchedEpisodeIds,
      Value<String> remoteRevision,
      Value<DateTime> capturedAt,
      Value<int> rowid,
    });

final class $$BangumiConflictSnapshotsTableReferences
    extends
        BaseReferences<
          _$WynimeDatabase,
          $BangumiConflictSnapshotsTable,
          BangumiConflictSnapshotRecord
        > {
  $$BangumiConflictSnapshotsTableReferences(
    super.$_db,
    super.$_table,
    super.$_typedResult,
  );

  static $BangumiAccountsTable _accountIdTable(_$WynimeDatabase db) =>
      db.bangumiAccounts.createAlias(
        'bangumi_conflict_snapshots__account_id__bangumi_accounts__account_id',
      );

  $$BangumiAccountsTableProcessedTableManager get accountId {
    final $_column = $_itemColumn<String>('account_id')!;

    final manager = $$BangumiAccountsTableTableManager(
      $_db,
      $_db.bangumiAccounts,
    ).filter((f) => f.accountId.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_accountIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }

  static $BangumiSubjectsTable _subjectIdTable(_$WynimeDatabase db) =>
      db.bangumiSubjects.createAlias(
        'bangumi_conflict_snapshots__subject_id__bangumi_subjects__subject_id',
      );

  $$BangumiSubjectsTableProcessedTableManager get subjectId {
    final $_column = $_itemColumn<String>('subject_id')!;

    final manager = $$BangumiSubjectsTableTableManager(
      $_db,
      $_db.bangumiSubjects,
    ).filter((f) => f.subjectId.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_subjectIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }
}

class $$BangumiConflictSnapshotsTableFilterComposer
    extends Composer<_$WynimeDatabase, $BangumiConflictSnapshotsTable> {
  $$BangumiConflictSnapshotsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get operationId => $composableBuilder(
    column: $table.operationId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get watchedEpisodeIds => $composableBuilder(
    column: $table.watchedEpisodeIds,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get remoteRevision => $composableBuilder(
    column: $table.remoteRevision,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get capturedAt => $composableBuilder(
    column: $table.capturedAt,
    builder: (column) => ColumnFilters(column),
  );

  $$BangumiAccountsTableFilterComposer get accountId {
    final $$BangumiAccountsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.accountId,
      referencedTable: $db.bangumiAccounts,
      getReferencedColumn: (t) => t.accountId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiAccountsTableFilterComposer(
            $db: $db,
            $table: $db.bangumiAccounts,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$BangumiSubjectsTableFilterComposer get subjectId {
    final $$BangumiSubjectsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.subjectId,
      referencedTable: $db.bangumiSubjects,
      getReferencedColumn: (t) => t.subjectId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiSubjectsTableFilterComposer(
            $db: $db,
            $table: $db.bangumiSubjects,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$BangumiConflictSnapshotsTableOrderingComposer
    extends Composer<_$WynimeDatabase, $BangumiConflictSnapshotsTable> {
  $$BangumiConflictSnapshotsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get operationId => $composableBuilder(
    column: $table.operationId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get watchedEpisodeIds => $composableBuilder(
    column: $table.watchedEpisodeIds,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get remoteRevision => $composableBuilder(
    column: $table.remoteRevision,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get capturedAt => $composableBuilder(
    column: $table.capturedAt,
    builder: (column) => ColumnOrderings(column),
  );

  $$BangumiAccountsTableOrderingComposer get accountId {
    final $$BangumiAccountsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.accountId,
      referencedTable: $db.bangumiAccounts,
      getReferencedColumn: (t) => t.accountId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiAccountsTableOrderingComposer(
            $db: $db,
            $table: $db.bangumiAccounts,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$BangumiSubjectsTableOrderingComposer get subjectId {
    final $$BangumiSubjectsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.subjectId,
      referencedTable: $db.bangumiSubjects,
      getReferencedColumn: (t) => t.subjectId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiSubjectsTableOrderingComposer(
            $db: $db,
            $table: $db.bangumiSubjects,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$BangumiConflictSnapshotsTableAnnotationComposer
    extends Composer<_$WynimeDatabase, $BangumiConflictSnapshotsTable> {
  $$BangumiConflictSnapshotsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get operationId => $composableBuilder(
    column: $table.operationId,
    builder: (column) => column,
  );

  GeneratedColumn<int> get status =>
      $composableBuilder(column: $table.status, builder: (column) => column);

  GeneratedColumn<String> get watchedEpisodeIds => $composableBuilder(
    column: $table.watchedEpisodeIds,
    builder: (column) => column,
  );

  GeneratedColumn<String> get remoteRevision => $composableBuilder(
    column: $table.remoteRevision,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get capturedAt => $composableBuilder(
    column: $table.capturedAt,
    builder: (column) => column,
  );

  $$BangumiAccountsTableAnnotationComposer get accountId {
    final $$BangumiAccountsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.accountId,
      referencedTable: $db.bangumiAccounts,
      getReferencedColumn: (t) => t.accountId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiAccountsTableAnnotationComposer(
            $db: $db,
            $table: $db.bangumiAccounts,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$BangumiSubjectsTableAnnotationComposer get subjectId {
    final $$BangumiSubjectsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.subjectId,
      referencedTable: $db.bangumiSubjects,
      getReferencedColumn: (t) => t.subjectId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$BangumiSubjectsTableAnnotationComposer(
            $db: $db,
            $table: $db.bangumiSubjects,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$BangumiConflictSnapshotsTableTableManager
    extends
        RootTableManager<
          _$WynimeDatabase,
          $BangumiConflictSnapshotsTable,
          BangumiConflictSnapshotRecord,
          $$BangumiConflictSnapshotsTableFilterComposer,
          $$BangumiConflictSnapshotsTableOrderingComposer,
          $$BangumiConflictSnapshotsTableAnnotationComposer,
          $$BangumiConflictSnapshotsTableCreateCompanionBuilder,
          $$BangumiConflictSnapshotsTableUpdateCompanionBuilder,
          (
            BangumiConflictSnapshotRecord,
            $$BangumiConflictSnapshotsTableReferences,
          ),
          BangumiConflictSnapshotRecord,
          PrefetchHooks Function({bool accountId, bool subjectId})
        > {
  $$BangumiConflictSnapshotsTableTableManager(
    _$WynimeDatabase db,
    $BangumiConflictSnapshotsTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$BangumiConflictSnapshotsTableFilterComposer(
                $db: db,
                $table: table,
              ),
          createOrderingComposer: () =>
              $$BangumiConflictSnapshotsTableOrderingComposer(
                $db: db,
                $table: table,
              ),
          createComputedFieldComposer: () =>
              $$BangumiConflictSnapshotsTableAnnotationComposer(
                $db: db,
                $table: table,
              ),
          updateCompanionCallback:
              ({
                Value<String> operationId = const Value.absent(),
                Value<String> accountId = const Value.absent(),
                Value<String> subjectId = const Value.absent(),
                Value<int?> status = const Value.absent(),
                Value<String> watchedEpisodeIds = const Value.absent(),
                Value<String> remoteRevision = const Value.absent(),
                Value<DateTime> capturedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => BangumiConflictSnapshotsCompanion(
                operationId: operationId,
                accountId: accountId,
                subjectId: subjectId,
                status: status,
                watchedEpisodeIds: watchedEpisodeIds,
                remoteRevision: remoteRevision,
                capturedAt: capturedAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String operationId,
                required String accountId,
                required String subjectId,
                Value<int?> status = const Value.absent(),
                required String watchedEpisodeIds,
                required String remoteRevision,
                required DateTime capturedAt,
                Value<int> rowid = const Value.absent(),
              }) => BangumiConflictSnapshotsCompanion.insert(
                operationId: operationId,
                accountId: accountId,
                subjectId: subjectId,
                status: status,
                watchedEpisodeIds: watchedEpisodeIds,
                remoteRevision: remoteRevision,
                capturedAt: capturedAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable(table),
                  $$BangumiConflictSnapshotsTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({accountId = false, subjectId = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [],
              addJoins:
                  <
                    T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic
                    >
                  >(state) {
                    if (accountId) {
                      state =
                          state.withJoin(
                                currentTable: table,
                                currentColumn: table.accountId,
                                referencedTable:
                                    $$BangumiConflictSnapshotsTableReferences
                                        ._accountIdTable(db),
                                referencedColumn:
                                    $$BangumiConflictSnapshotsTableReferences
                                        ._accountIdTable(db)
                                        .accountId,
                              )
                              as T;
                    }
                    if (subjectId) {
                      state =
                          state.withJoin(
                                currentTable: table,
                                currentColumn: table.subjectId,
                                referencedTable:
                                    $$BangumiConflictSnapshotsTableReferences
                                        ._subjectIdTable(db),
                                referencedColumn:
                                    $$BangumiConflictSnapshotsTableReferences
                                        ._subjectIdTable(db)
                                        .subjectId,
                              )
                              as T;
                    }

                    return state;
                  },
              getPrefetchedDataCallback: (items) async {
                return [];
              },
            );
          },
        ),
      );
}

typedef $$BangumiConflictSnapshotsTableProcessedTableManager =
    ProcessedTableManager<
      _$WynimeDatabase,
      $BangumiConflictSnapshotsTable,
      BangumiConflictSnapshotRecord,
      $$BangumiConflictSnapshotsTableFilterComposer,
      $$BangumiConflictSnapshotsTableOrderingComposer,
      $$BangumiConflictSnapshotsTableAnnotationComposer,
      $$BangumiConflictSnapshotsTableCreateCompanionBuilder,
      $$BangumiConflictSnapshotsTableUpdateCompanionBuilder,
      (
        BangumiConflictSnapshotRecord,
        $$BangumiConflictSnapshotsTableReferences,
      ),
      BangumiConflictSnapshotRecord,
      PrefetchHooks Function({bool accountId, bool subjectId})
    >;

class $WynimeDatabaseManager {
  final _$WynimeDatabase _db;
  $WynimeDatabaseManager(this._db);
  $$AppSettingsRowsTableTableManager get appSettingsRows =>
      $$AppSettingsRowsTableTableManager(_db, _db.appSettingsRows);
  $$WatchHistoryRowsTableTableManager get watchHistoryRows =>
      $$WatchHistoryRowsTableTableManager(_db, _db.watchHistoryRows);
  $$ArtifactManifestsTableTableManager get artifactManifests =>
      $$ArtifactManifestsTableTableManager(_db, _db.artifactManifests);
  $$ArtifactRowsTableTableManager get artifactRows =>
      $$ArtifactRowsTableTableManager(_db, _db.artifactRows);
  $$DeleteJobRowsTableTableManager get deleteJobRows =>
      $$DeleteJobRowsTableTableManager(_db, _db.deleteJobRows);
  $$BangumiAccountsTableTableManager get bangumiAccounts =>
      $$BangumiAccountsTableTableManager(_db, _db.bangumiAccounts);
  $$BangumiSchedulesTableTableManager get bangumiSchedules =>
      $$BangumiSchedulesTableTableManager(_db, _db.bangumiSchedules);
  $$BangumiSubjectsTableTableManager get bangumiSubjects =>
      $$BangumiSubjectsTableTableManager(_db, _db.bangumiSubjects);
  $$BangumiCollectionsTableTableManager get bangumiCollections =>
      $$BangumiCollectionsTableTableManager(_db, _db.bangumiCollections);
  $$BangumiEpisodesTableTableManager get bangumiEpisodes =>
      $$BangumiEpisodesTableTableManager(_db, _db.bangumiEpisodes);
  $$BangumiEpisodeCollectionsTableTableManager get bangumiEpisodeCollections =>
      $$BangumiEpisodeCollectionsTableTableManager(
        _db,
        _db.bangumiEpisodeCollections,
      );
  $$BangumiMappingsTableTableManager get bangumiMappings =>
      $$BangumiMappingsTableTableManager(_db, _db.bangumiMappings);
  $$BangumiSyncOperationsTableTableManager get bangumiSyncOperations =>
      $$BangumiSyncOperationsTableTableManager(_db, _db.bangumiSyncOperations);
  $$BangumiConflictSnapshotsTableTableManager get bangumiConflictSnapshots =>
      $$BangumiConflictSnapshotsTableTableManager(
        _db,
        _db.bangumiConflictSnapshots,
      );
}
