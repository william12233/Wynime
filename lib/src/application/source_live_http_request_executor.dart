import '../domain/models/source_http_models.dart';
import '../domain/services/source_http_transport.dart';
import 'source_live_http_request_coordinator.dart';

enum SourceLiveHttpExecutionStatus { completed, notCompleted }

enum SourceLiveHttpExecutionFailureStage { admission, transport }

/// The bounded result of package admission followed by one live HTTP GET.
///
/// A completed result retains the response in memory for a later declarative
/// evaluator. A rejected result retains exactly one typed admission or
/// transport failure and never exposes response data.
final class SourceLiveHttpExecutionResult {
  SourceLiveHttpExecutionResult({
    required this.status,
    this.response,
    this.failureStage,
    this.admissionResult,
    this.transportResult,
  }) {
    final isCompleted = status == SourceLiveHttpExecutionStatus.completed;
    final childCount = [
      admissionResult,
      transportResult,
    ].where((value) => value != null).length;
    if (isCompleted) {
      if (response == null || failureStage != null || childCount != 0) {
        throw ArgumentError(
          'A completed HTTP execution must contain only one response.',
        );
      }
      return;
    }
    if (response != null || failureStage == null || childCount != 1) {
      throw ArgumentError(
        'A rejected HTTP execution must contain one typed failure.',
      );
    }
    if (failureStage == SourceLiveHttpExecutionFailureStage.admission &&
        (admissionResult == null ||
            admissionResult!.status == SourceLiveHttpRequestStatus.ready ||
            transportResult != null)) {
      throw ArgumentError(
        'An admission failure must contain only a non-ready admission result.',
      );
    }
    if (failureStage == SourceLiveHttpExecutionFailureStage.transport &&
        (transportResult == null ||
            transportResult!.status == SourceHttpTransportStatus.success ||
            admissionResult != null)) {
      throw ArgumentError(
        'A transport failure must contain only a failed transport result.',
      );
    }
  }

  final SourceLiveHttpExecutionStatus status;
  final SourceHttpResponse? response;
  final SourceLiveHttpExecutionFailureStage? failureStage;
  final SourceLiveHttpRequestResult? admissionResult;
  final SourceHttpTransportResult? transportResult;

  Map<String, Object?> toRedactedDiagnostic() => {
    'status': status.name,
    'hasResponse': response != null,
    'failureStage': failureStage?.name,
    'admissionStatus': admissionResult?.status.name,
    'transportStatus': transportResult?.status.name,
    'reasonCode': admissionResult?.reasonCode ?? transportResult?.reasonCode,
  };

  @override
  String toString() => toRedactedDiagnostic().toString();
}

/// Executes only a ready request from [SourceLiveHttpRequestCoordinator].
///
/// It does not evaluate source rules, normalize records, touch WebView or own
/// persistence. Transport failures remain typed and are never reported as a
/// completed response.
final class SourceLiveHttpRequestExecutor {
  const SourceLiveHttpRequestExecutor({
    required this.requestCoordinator,
    required this.transport,
  });

  final SourceLiveHttpRequestCoordinator requestCoordinator;
  final SourceHttpTransport transport;

  Future<SourceLiveHttpExecutionResult> execute(
    SourceLiveHttpRequestPlan plan,
  ) async {
    final admission = requestCoordinator.prepare(plan);
    if (admission.status != SourceLiveHttpRequestStatus.ready) {
      return SourceLiveHttpExecutionResult(
        status: SourceLiveHttpExecutionStatus.notCompleted,
        failureStage: SourceLiveHttpExecutionFailureStage.admission,
        admissionResult: admission,
      );
    }

    SourceHttpTransportResult transportResult;
    try {
      transportResult = await transport.send(admission.request!);
    } on Object {
      transportResult = SourceHttpTransportResult(
        status: SourceHttpTransportStatus.failed,
        reasonCode: 'source_http_failed',
      );
    }
    if (transportResult.status != SourceHttpTransportStatus.success) {
      return SourceLiveHttpExecutionResult(
        status: SourceLiveHttpExecutionStatus.notCompleted,
        failureStage: SourceLiveHttpExecutionFailureStage.transport,
        transportResult: transportResult,
      );
    }

    return SourceLiveHttpExecutionResult(
      status: SourceLiveHttpExecutionStatus.completed,
      response: transportResult.response,
    );
  }
}
