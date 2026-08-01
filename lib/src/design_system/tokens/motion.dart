import 'package:flutter/animation.dart';

abstract final class WynimeMotion {
  static const Duration fast = Duration(milliseconds: 120);
  static const Duration standard = Duration(milliseconds: 220);
  static const Duration emphasized = Duration(milliseconds: 320);
  static const Curve standardCurve = Curves.easeOutCubic;
}
