package jp.co.sss.lms.util;

import java.text.ParseException;
import java.util.Date;
import java.util.LinkedHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.mapper.MSectionMapper;

/**
 * 勤怠管理のユーティリティクラス
 * 
 * @author 東京ITスクール
 */
@Component
public class AttendanceUtil {

	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private MSectionMapper mSectionMapper;

	/**
	 * SSS定時・出退勤時間を元に、遅刻早退を判定をする
	 * 
	 * @param trainingStartTime 開始時刻
	 * @param trainingEndTime   終了時刻
	 * @return 遅刻早退を判定メソッド
	 */
	public AttendanceStatusEnum getStatus(TrainingTime trainingStartTime,
			TrainingTime trainingEndTime) {
		return getStatus(trainingStartTime, trainingEndTime, Constants.SSS_WORK_START_TIME,
				Constants.SSS_WORK_END_TIME);
	}

	/**
	 * 与えられた定時・出退勤時間を元に、遅刻早退を判定する
	 * 
	 * @param trainingStartTime 開始時刻
	 * @param trainingEndTime   終了時刻
	 * @param workStartTime     定時開始時刻
	 * @param workEndTime       定時終了時刻
	 * @return 判定結果
	 */
	private AttendanceStatusEnum getStatus(TrainingTime trainingStartTime,
			TrainingTime trainingEndTime, TrainingTime workStartTime, TrainingTime workEndTime) {
		// 定時が不明な場合、NONEを返却する
		if (workStartTime == null || workStartTime.isBlank() || workEndTime == null
				|| workEndTime.isBlank()) {
			return AttendanceStatusEnum.NONE;
		}
		boolean isLate = false, isEarly = false;
		// 定時より1分以上遅く出社していたら遅刻(＝はセーフ)
		if (trainingStartTime != null && trainingStartTime.isNotBlank()) {
			isLate = (trainingStartTime.compareTo(workStartTime) > 0);
		}
		// 定時より1分以上早く退社していたら早退(＝はセーフ)
		if (trainingEndTime != null && trainingEndTime.isNotBlank()) {
			isEarly = (trainingEndTime.compareTo(workEndTime) < 0);
		}
		if (isLate && isEarly) {
			return AttendanceStatusEnum.TARDY_AND_LEAVING_EARLY;
		}
		if (isLate) {
			return AttendanceStatusEnum.TARDY;
		}
		if (isEarly) {
			return AttendanceStatusEnum.LEAVING_EARLY;
		}
		return AttendanceStatusEnum.NONE;
	}

	/**
	 * 中抜け時間を時(hour)と分(minute)に変換
	 *
	 * @param min 中抜け時間
	 * @return 時(hour)と分(minute)に変換したクラス
	 */
	public TrainingTime calcBlankTime(int min) {
		int hour = min / 60;
		int minute = min % 60;
		TrainingTime total = new TrainingTime(hour, minute);
		return total;
	}

	/**
	 * 時刻分を丸めた本日日付を取得
	 * 
	 * @return "yyyy/M/d"形式の日付
	 */
	public Date getTrainingDate() {
		Date trainingDate;
		try {
			trainingDate = dateUtil.parse(dateUtil.toString(new Date()));
		} catch (ParseException e) {
			// DateUtil#toStringとparseは同様のフォーマットを使用しているため、起こりえないエラー
			throw new IllegalStateException();
		}
		return trainingDate;
	}

	/**
	 * 休憩時間取得
	 * 
	 * @return 休憩時間
	 */
	public LinkedHashMap<Integer, String> setBlankTime() {
		LinkedHashMap<Integer, String> map = new LinkedHashMap<>();
		map.put(null, "");
		for (int i = 15; i < 480;) {
			int hour = i / 60;
			int minute = i % 60;
			String time;

			if (hour == 0) {
				time = minute + "分";

			} else if (minute == 0) {
				time = hour + "時間";
			} else {
				time = hour + "時" + minute + "分";
			}

			map.put(i, time);

			i = i + 15;

		}
		return map;
	}

	/**
	 * 勤怠時間の時間設定
	 * @return 時間
	 */
	public LinkedHashMap<Integer, String> getHourMap() {
		LinkedHashMap<Integer, String> map = new LinkedHashMap<>();
		map.put(null, "");
		String time;
		//00～23までの値を登録
		for (int i = 0; i < 24; i++) {
			if (i < 10) {
				time = ("0" + i);
			} else {
				time = ("" + i);
			}
			map.put(i, time);
		}
		return map;
	}
	
	/**
	 * 勤怠時間の分設定
	 * @return 分
	 */
	public LinkedHashMap<Integer, String> getMinuteMap() {
		LinkedHashMap<Integer, String> map = new LinkedHashMap<>();
		map.put(null, "");
		String time;
		//00～59までの値を登録
		for (int i = 0; i < 60; i++) {
			if (i < 10) {
				time = ("0" + i);
			} else {
				time = ("" + i);
			}
			map.put(i, time);
		}
		return map;
	}
	
	/**
	 * 受講時間数を算出
	 * @param time
	 * @return 受講時間数
	 */
	public TrainingTime calcJukoTime(TrainingTime time) {
		int hour ;
		int minute ;
		int trainingMinute = 0;
		Integer startHour = null;
		Integer startMinute = null;
		Integer endHour = null;
		Integer endMinute = null;
		
		if (time.getTrainingStartTime() != null && time.getTrainingEndTime() != null) {
			String startTime = time.getTrainingStartTime();
			String endTime = time.getTrainingEndTime();
			if (!startTime.equals("") && !endTime.equals("")) {
				//空文字じゃなければ「：」を中心に分割して出勤時間、分をセット
				String[] startTimes = startTime.split(":");
				startHour = Integer.parseInt(startTimes[0]);
				startMinute = Integer.parseInt(startTimes[1]);
				//空文字じゃなければ「：」を中心に分割して退勤時間、分をセット
				String[] endTimes = endTime.split(":");
				endHour = Integer.parseInt(endTimes[0]);
				endMinute = Integer.parseInt(endTimes[1]);
				
				hour = (endHour - startHour) * 60;
				minute = endMinute - startMinute;
				trainingMinute = hour + minute;
				
				time.setTrainingTime(trainingMinute);
			}
		}
		return time;
	}
	
	/**
	 * 研修日の判定
	 * 
	 * @param courseId
	 * @param trainingDate
	 * @return 判定結果
	 */
	public boolean isWorkDay(Integer courseId, Date trainingDate) {
		Integer count = mSectionMapper.getSectionCountByCourseId(courseId, trainingDate);
		if (count > 0) {
			return true;
		}
		return false;
	}

}
