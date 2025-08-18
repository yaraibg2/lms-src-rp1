package jp.co.sss.lms.service;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.entity.TStudentAttendance;
import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.form.DailyAttendanceForm;
import jp.co.sss.lms.mapper.TStudentAttendanceMapper;
import jp.co.sss.lms.util.AttendanceUtil;
import jp.co.sss.lms.util.Constants;
import jp.co.sss.lms.util.DateUtil;
import jp.co.sss.lms.util.LoginUserUtil;
import jp.co.sss.lms.util.MessageUtil;
import jp.co.sss.lms.util.TrainingTime;

/**
 * 勤怠情報（受講生入力）サービス
 * 
 * @author 東京ITスクール
 */

@Service
public class StudentAttendanceService {

	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private AttendanceUtil attendanceUtil;
	@Autowired
	private MessageUtil messageUtil;
	@Autowired
	private LoginUserUtil loginUserUtil;
	@Autowired
	private LoginUserDto loginUserDto;
	@Autowired
	private TStudentAttendanceMapper tStudentAttendanceMapper;
	@Autowired
	private MessageSource messageSource;

	/**
	 * 勤怠一覧情報取得
	 * 
	 * @param courseId
	 * @param lmsUserId
	 * @return 勤怠管理画面用DTOリスト
	 */
	public List<AttendanceManagementDto> getAttendanceManagement(Integer courseId,
			Integer lmsUserId) {

		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = tStudentAttendanceMapper
				.getAttendanceManagement(courseId, lmsUserId, Constants.DB_FLG_FALSE);
		for (AttendanceManagementDto dto : attendanceManagementDtoList) {
			// 中抜け時間を設定
			if (dto.getBlankTime() != null) {
				TrainingTime blankTime = attendanceUtil.calcBlankTime(dto.getBlankTime());
				dto.setBlankTimeValue(String.valueOf(blankTime));
			}
			// 遅刻早退区分判定
			AttendanceStatusEnum statusEnum = AttendanceStatusEnum.getEnum(dto.getStatus());
			if (statusEnum != null) {
				dto.setStatusDispName(statusEnum.name);
			}
		}

		return attendanceManagementDtoList;
	}
	
	/**
	 * 未入力があるかどうかを確認
	 * 
	 * @param courseId
	 * @param lmsUserId
	 * @return 未入力チェック
	 */
	public boolean checkAttendanceBlank(Integer lmsUserId) {
		//未入力数の確認
		int checked = tStudentAttendanceMapper.notEnterCount(lmsUserId, Constants.DB_FLG_FALSE, new Date());
		
		if (checked > 0) {
			return true;
		}
		return false;
	}

	/**
	 * 出退勤更新前のチェック
	 * 
	 * @param attendanceType
	 * @return エラーメッセージ
	 */
	public String punchCheck(Short attendanceType) {
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 権限チェック
		if (!loginUserUtil.isStudent()) {
			return messageUtil.getMessage(Constants.VALID_KEY_AUTHORIZATION);
		}
		// 研修日チェック
		if (!attendanceUtil.isWorkDay(loginUserDto.getCourseId(), trainingDate)) {
			return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_NOTWORKDAY);
		}
		// 登録情報チェック
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		switch (attendanceType) {
		case Constants.CODE_VAL_ATWORK:
			if (tStudentAttendance != null
					&& !tStudentAttendance.getTrainingStartTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			break;
		case Constants.CODE_VAL_LEAVING:
			if (tStudentAttendance == null
					|| tStudentAttendance.getTrainingStartTime().equals("")) {
				// 出勤情報がないため退勤情報を入力出来ません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
			}
			if (!tStudentAttendance.getTrainingEndTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			TrainingTime trainingStartTime = new TrainingTime(
					tStudentAttendance.getTrainingStartTime());
			TrainingTime trainingEndTime = new TrainingTime();
			if (trainingStartTime.compareTo(trainingEndTime) > 0) {
				// 退勤時刻は出勤時刻より後でなければいけません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
			}
			break;
		}
		return null;
	}

	/**
	 * 出勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchIn() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 現在の研修時刻
		TrainingTime trainingStartTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				null);
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		if (tStudentAttendance == null) {
			// 登録処理
			tStudentAttendance = new TStudentAttendance();
			tStudentAttendance.setLmsUserId(loginUserDto.getLmsUserId());
			tStudentAttendance.setTrainingDate(trainingDate);
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setTrainingEndTime("");
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setNote("");
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setFirstCreateDate(date);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendance.setBlankTime(null);
			tStudentAttendanceMapper.insert(tStudentAttendance);
		} else {
			// 更新処理
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendanceMapper.update(tStudentAttendance);
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 退勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchOut() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		// 出退勤時刻
		TrainingTime trainingStartTime = new TrainingTime(
				tStudentAttendance.getTrainingStartTime());
		TrainingTime trainingEndTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				trainingEndTime);
		// 更新処理
		tStudentAttendance.setTrainingEndTime(trainingEndTime.toString());
		tStudentAttendance.setStatus(attendanceStatusEnum.code);
		tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
		tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
		tStudentAttendance.setLastModifiedDate(date);
		tStudentAttendanceMapper.update(tStudentAttendance);
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 勤怠フォームへ設定
	 * 
	 * @param attendanceManagementDtoList
	 * @return 勤怠編集フォーム
	 */
	public AttendanceForm setAttendanceForm(
			List<AttendanceManagementDto> attendanceManagementDtoList) {

		AttendanceForm attendanceForm = new AttendanceForm();
		attendanceForm.setAttendanceList(new ArrayList<DailyAttendanceForm>());
		attendanceForm.setLmsUserId(loginUserDto.getLmsUserId());
		attendanceForm.setUserName(loginUserDto.getUserName());
		attendanceForm.setLeaveFlg(loginUserDto.getLeaveFlg());
		attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());

		// 途中退校している場合のみ設定
		if (loginUserDto.getLeaveDate() != null) {
			attendanceForm
					.setLeaveDate(dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy-MM-dd"));
			attendanceForm.setDispLeaveDate(
					dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy年M月d日"));
		}

		// 勤怠管理リストの件数分、日次の勤怠フォームに移し替え
		for (AttendanceManagementDto attendanceManagementDto : attendanceManagementDtoList) {
			DailyAttendanceForm dailyAttendanceForm = new DailyAttendanceForm();
			dailyAttendanceForm
					.setStudentAttendanceId(attendanceManagementDto.getStudentAttendanceId());
			dailyAttendanceForm
					.setTrainingDate(dateUtil.toString(attendanceManagementDto.getTrainingDate()));
			dailyAttendanceForm
					.setTrainingStartTime(attendanceManagementDto.getTrainingStartTime());
			dailyAttendanceForm.setTrainingEndTime(attendanceManagementDto.getTrainingEndTime());
			if (attendanceManagementDto.getBlankTime() != null) {
				dailyAttendanceForm.setBlankTime(attendanceManagementDto.getBlankTime());
				dailyAttendanceForm.setBlankTimeValue(String.valueOf(
						attendanceUtil.calcBlankTime(attendanceManagementDto.getBlankTime())));
			}
			dailyAttendanceForm.setStatus(String.valueOf(attendanceManagementDto.getStatus()));
			dailyAttendanceForm.setNote(attendanceManagementDto.getNote());
			dailyAttendanceForm.setSectionName(attendanceManagementDto.getSectionName());
			dailyAttendanceForm.setIsToday(attendanceManagementDto.getIsToday());
			dailyAttendanceForm.setDispTrainingDate(dateUtil
					.dateToString(attendanceManagementDto.getTrainingDate(), "yyyy年M月d日(E)"));
			dailyAttendanceForm.setStatusDispName(attendanceManagementDto.getStatusDispName());

			attendanceForm.getAttendanceList().add(dailyAttendanceForm);
		}

		return attendanceForm;
	}
	
	/**
	 * 勤怠時間の時間設定
	 * @return 時間
	 */
	public List<String> setHours() {
		List<String> hours = new ArrayList<>();
		for (int i = 0; i < 24; i++) {
			if (i < 10) {
				hours.add("0" + i);
			} else {
				hours.add("" + i);
			}
		}
		return hours;
	}
	
	/**
	 * 勤怠時間の分設定
	 * @return 分
	 */
	public List<String> setMinutes() {
		List<String> minutes = new ArrayList<>();
		for (int i = 0; i < 60; i++) {
			if (i < 10) {
				minutes.add("0" + i);
			} else {
				minutes.add("" + i);
			}
		}
		return minutes;
	}

	/**
	 * 勤怠登録・更新処理
	 * 
	 * @param attendanceForm
	 * @return 完了メッセージ
	 * @throws ParseException
	 */
	public String update(AttendanceForm attendanceForm) throws ParseException {

		Integer lmsUserId = loginUserUtil.isStudent() ? loginUserDto.getLmsUserId()
				: attendanceForm.getLmsUserId();

		// 現在の勤怠情報（受講生入力）リストを取得
		List<TStudentAttendance> tStudentAttendanceList = tStudentAttendanceMapper
				.findByLmsUserId(lmsUserId, Constants.DB_FLG_FALSE);

		// 入力された情報を更新用のエンティティに移し替え
		Date date = new Date();
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {

			// 更新用エンティティ作成
			TStudentAttendance tStudentAttendance = new TStudentAttendance();
			// 日次勤怠フォームから更新用のエンティティにコピー
			BeanUtils.copyProperties(dailyAttendanceForm, tStudentAttendance);
			// 研修日付
			tStudentAttendance
					.setTrainingDate(dateUtil.parse(dailyAttendanceForm.getTrainingDate()));
			// 現在の勤怠情報リストのうち、研修日が同じものを更新用エンティティで上書き
			for (TStudentAttendance entity : tStudentAttendanceList) {
				if (entity.getTrainingDate().equals(tStudentAttendance.getTrainingDate())) {
					tStudentAttendance = entity;
					break;
				}
			}
			tStudentAttendance.setLmsUserId(lmsUserId);
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			// 出勤時刻整形
			TrainingTime trainingStartTime = null;
			trainingStartTime = new TrainingTime(dailyAttendanceForm.getTrainingStartTime());
			tStudentAttendance.setTrainingStartTime(trainingStartTime.getFormattedString());
			// 退勤時刻整形
			TrainingTime trainingEndTime = null;
			trainingEndTime = new TrainingTime(dailyAttendanceForm.getTrainingEndTime());
			tStudentAttendance.setTrainingEndTime(trainingEndTime.getFormattedString());
			// 中抜け時間
			tStudentAttendance.setBlankTime(dailyAttendanceForm.getBlankTime());
			// 遅刻早退ステータス
			if ((trainingStartTime != null || trainingEndTime != null)
					&& !dailyAttendanceForm.getStatusDispName().equals("欠席")) {
				AttendanceStatusEnum attendanceStatusEnum = attendanceUtil
						.getStatus(trainingStartTime, trainingEndTime);
				tStudentAttendance.setStatus(attendanceStatusEnum.code);
			}
			// 備考
			tStudentAttendance.setNote(dailyAttendanceForm.getNote());
			// 更新者と更新日時
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			// 削除フラグ
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			// 登録用Listへ追加
			tStudentAttendanceList.add(tStudentAttendance);
		}
		// 登録・更新処理
		for (TStudentAttendance tStudentAttendance : tStudentAttendanceList) {
			if (tStudentAttendance.getStudentAttendanceId() == null) {
				tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
				tStudentAttendance.setFirstCreateDate(date);
				tStudentAttendanceMapper.insert(tStudentAttendance);
			} else {
				tStudentAttendanceMapper.update(tStudentAttendance);
			}
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}
	
	/**
	 * 講義時間を時間と分に分割
	 * @param forms
	 * @return 設定後のフォーム
	 */
	public List<DailyAttendanceForm> setTimes(List<DailyAttendanceForm> forms) {
		List<DailyAttendanceForm> newForm = new ArrayList<>();
		for (DailyAttendanceForm form : forms) {
			if (form.getTrainingStartTime() != null
					|| form.getTrainingEndTime() != null) {
				String startTime = form.getTrainingStartTime();
				String endTime = form.getTrainingEndTime();
				
				if (!startTime.equals("")) {
					String[] startTimes = startTime.split(":");
					form.setStartHours(startTimes[0]);
					form.setStartMinutes(startTimes[1]);
				}
				
				if (!endTime.equals("")) {
				String[] endTimes = endTime.split(":");
				form.setEndHours(endTimes[0]);
				form.setEndMinutes(endTimes[1]);
				}
			}
			newForm.add(form);
		}
		return newForm;
	}
	
	/**
	 * 時間と分の結合
	 * @param forms
	 * @return 結合後のフォーム
	 */
	public List<DailyAttendanceForm> unionTimes(List<DailyAttendanceForm> forms) {
		List<DailyAttendanceForm> newForm = new ArrayList<>();
		for (DailyAttendanceForm form : forms) {
			if (form.getStartHours() != null
					&& form.getStartMinutes() != null
					&& form.getEndHours() != null
					&& form.getEndMinutes() != null) {
				form.setTrainingStartTime(form.getStartHours() + ":" + form.getStartMinutes());
				form.setTrainingEndTime(form.getEndHours() + ":" + form.getEndMinutes());
			}
			if (form.getStartHours().equals("0")) {
				form.setStartHours(null);
			}
			if (form.getStartMinutes().equals("0")) {
				form.setStartMinutes(null);
			}
			if (form.getEndHours().equals("0")) {
				form.setEndHours(null);
			}
			if (form.getEndMinutes().equals("0")) {
				form.setEndMinutes(null);
			}
			if (form.getTrainingStartTime().equals("0:0") && form.getTrainingEndTime().equals("0:0")) {
				form.setStartHours(null);
				form.setStartMinutes(null);
				form.setEndHours(null);
				form.setEndMinutes(null);
				form.setTrainingStartTime(null);
				form.setTrainingEndTime(null);
			}
			newForm.add(form);
		}
		return newForm;
	}
	
	public BindingResult punchCheck(AttendanceForm forms, BindingResult result) {
		List<String> errorList = new ArrayList<>();
		int i = 0;
		for (DailyAttendanceForm form : forms.getAttendanceList()) {
			if (form.getNote().length() > 100) {
				String[] str = { messageSource.getMessage("placeNote", new String[] {}, Locale.getDefault()), "100" };
				String error = messageUtil.getMessage(Constants.VALID_KEY_MAXLENGTH, str);
				FieldError fieldError = new FieldError(result.getObjectName(), "note", error);
				result.addError(fieldError);
				errorList.add(error);
			}
			if (form.getStartHours() != null && form.getStartMinutes() == null) {
				String[] str = { "出勤時間" };
				String error = messageUtil.getMessage(Constants.INPUT_INVALID, str);
				FieldError fieldError = new FieldError(result.getObjectName(), "startMinutes", error);
				result.addError(fieldError);
				errorList.add(error);
			}
			if (form.getStartMinutes() != null && form.getStartHours() == null) {
				String[] str = { "出勤時間" };
				String error = messageUtil.getMessage(Constants.INPUT_INVALID, str);
				FieldError fieldError = new FieldError(result.getObjectName(), "startHours", error);
				result.addError(fieldError);
				errorList.add(error);
			}
			if (form.getEndHours() != null && form.getEndMinutes() == null) {
				String[] str = { "退勤時間" };
				String error = messageUtil.getMessage(Constants.INPUT_INVALID, str);
				FieldError fieldError = new FieldError(result.getObjectName(), "endMinutes", error);
				result.addError(fieldError);
				errorList.add(error);
			}
			if (form.getEndMinutes() != null && form.getEndHours() == null) {
				String[] str = { "退勤時間" };
				String error = messageUtil.getMessage(Constants.INPUT_INVALID, str);
				FieldError fieldError = new FieldError(result.getObjectName(), "endHours", error);
				result.addError(fieldError);
				errorList.add(error);
			}
			if (form.getTrainingStartTime() == null && form.getTrainingEndTime() != null) {
				String error = messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
				FieldError fieldError = new FieldError(result.getObjectName(), "startHours", error);
				result.addError(fieldError);
				errorList.add(error);
			}
			if (form.getStartHours() != null
					&& form.getStartMinutes() != null
					&& form.getEndHours() != null
					&& form.getEndMinutes() != null) {
				Integer startHour = Integer.parseInt(form.getStartHours());
				Integer startMinute = Integer.parseInt(form.getStartMinutes());
				Integer endHour = Integer.parseInt(form.getEndHours());
				Integer endMinute = Integer.parseInt(form.getEndMinutes());
				
				if (startHour != null && startMinute != null && endHour != null && endMinute != null) {
					if (startHour > endHour) {
						String[] list = { i + "" };
						String error = messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE, list);
						FieldError fieldError = new FieldError(result.getObjectName(), "trainingTimeOver", error);
						result.addError(fieldError);
						errorList.add(error);
					} else if (startHour == endHour && startMinute > endMinute) {
						String[] list = { i + "" };
						String error = messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE, list);
						FieldError fieldError = new FieldError(result.getObjectName(), "trainingTimeOver", error);
						result.addError(fieldError);
						errorList.add(error);
					}
				}
				int hour ;
				int minute ;
				int trainingMinute = 0;
				if(startHour != null && startMinute != null && 
						endHour != null && endMinute != null) {
					hour = (endHour - startHour) * 60;
					minute = endMinute - startMinute;
					trainingMinute = hour + minute;
				}
				if (form.getBlankTime() != null && trainingMinute < form.getBlankTime()) {
					String error = messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_BLANKTIMEERROR);
					FieldError fieldError = new FieldError(result.getObjectName(),"blankTime",error);
					result.addError(fieldError);
					errorList.add(error);
				}
			
			}
			i++;
		}
		forms.setErrorList(errorList);
		return result;
	}
	
	public AttendanceForm setBlankTime(AttendanceForm forms) {
		List<DailyAttendanceForm> newForm = new ArrayList<>();
		for (DailyAttendanceForm form : forms.getAttendanceList()) {
			// 中抜け時間を設定
			if (form.getBlankTime() != null) {
				TrainingTime blankTime = attendanceUtil.calcBlankTime(form.getBlankTime());
				form.setBlankTimeValue(String.valueOf(blankTime));
			}
			newForm.add(form);
		}
		forms.setBlankTimes(attendanceUtil.setBlankTime());
		forms.setAttendanceList(newForm);
		return forms;
	}
}
