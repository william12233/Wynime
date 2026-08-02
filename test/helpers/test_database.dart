import 'package:drift/native.dart';
import 'package:wynime/src/infrastructure/database/wynime_database.dart';

WynimeDatabase openTestDatabase() {
  return WynimeDatabase(NativeDatabase.memory());
}
