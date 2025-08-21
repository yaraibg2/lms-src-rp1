package jp.co.sss.lms.form;

import lombok.Data;

/**
 * 勤怠情報確認フォーム
 * 
 * @author 東京ITスクール
 */
@Data
public class AttendanceCheckForm {
	/** lmsユーザーID */
	private Integer lmsUserId;
	/** ユーザーID */
	private Integer userId;
	/** ユーザー名 */
	private String userName;
	/** コースID */
	private Integer courseId;
	/** 企業ID */
	private Integer companyId;
	/** 会場ID */
	private Integer placeId;
}
