package jp.co.sss.lms.form;

import java.util.List;

import lombok.Data;

/**
 * 勤怠一括登録フォーム
 */
@Data
public class BulkRegistForm {
	/** 検索開始日 */
	private String searchPeriodFrom;
	/** 検索終了日 */
	private String searchPeriodTo;
	/** 会場ID */
	private Integer placeId;
	/** 日次勤怠リスト */
	private List<DailyAttendanceForm> attendanceList;
}
