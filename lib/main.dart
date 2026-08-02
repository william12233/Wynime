import 'package:flutter/widgets.dart';
import 'package:wynime/src/app/wynime_app.dart';
import 'package:wynime/src/platform/playback/media_kit_facade.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  ProductionMediaKitFacade.ensureInitialized();
  runApp(const WynimeApp());
}
