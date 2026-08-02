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
  Set<GeneratedColumn> get $primaryKey => {artifactId};
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
}
