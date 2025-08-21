package jp.co.sss.lms.entity;

import lombok.Data;

/**
 * 勤怠情報確認フォーム
 * 
 * @author 東京ITスクール
 */
@Data
public class AttendanceCheck {
	/** ユーザーID */
	private Integer userId;
	/** ユーザー名 */
	private String userName;
	/** コースID */
	private Integer courseId;
	/** コース名 */
	private String courseName;
	/** 企業ID */
	private Integer companyId;
	/** 企業名 */
	private String companyName;
	/** 会場ID */
	private Integer placeId;
	/** 会場名 */
	private String placeName;
	/** 会場の備考 */
	private String placeNote;
}