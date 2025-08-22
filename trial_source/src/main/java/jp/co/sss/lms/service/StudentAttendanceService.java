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
import jp.co.sss.lms.entity.AttendanceCheck;
import jp.co.sss.lms.entity.TStudentAttendance;
import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.form.AttendanceCheckForm;
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
		attendanceForm.setHours(attendanceUtil.getHourMap());
		attendanceForm.setMinutes(attendanceUtil.getMinuteMap());
		
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
				//空文字じゃなければ「：」を中心に分割して出勤時間、分をセット
				if (!startTime.equals("")) {
					String[] startTimes = startTime.split(":");
					form.setTrainingStartTimeHour(startTimes[0]);
					form.setTrainingStartTimeMinute(startTimes[1]);
				}
				//空文字じゃなければ「：」を中心に分割して退勤時間、分をセット
				if (!endTime.equals("")) {
				String[] endTimes = endTime.split(":");
				form.setTrainingEndTimeHour(endTimes[0]);
				form.setTrainingEndTimeMinute(endTimes[1]);
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
			//未入力のフィールドにnullを代入
			if (form.getTrainingStartTimeHour().equals("")) {
				form.setTrainingStartTimeHour(null);;
			}
			if (form.getTrainingStartTimeMinute().equals("")) {
				form.setTrainingStartTimeMinute(null);
			}
			if (form.getTrainingEndTimeHour().equals("")) {
				form.setTrainingEndTimeHour(null);
			}
			if (form.getTrainingEndTimeMinute().equals("")) {
				form.setTrainingEndTimeMinute(null);;
			}
			//時間、分を時刻に統合
			if (form.getTrainingStartTimeHour() != null
					&& form.getTrainingStartTimeMinute() != null) {
				form.setTrainingStartTime(form.getTrainingStartTimeHour() + ":" + form.getTrainingStartTimeMinute());	
			}
			if(form.getTrainingEndTimeHour() != null
					&& form.getTrainingEndTimeMinute() != null) {
				form.setTrainingEndTime(form.getTrainingEndTimeHour() + ":" + form.getTrainingEndTimeMinute());
			}
			newForm.add(form);
		}
		return newForm;
	}
	
	/**
	 * 独自入力チェック
	 * @param forms
	 * @param result
	 * @return BindingResult
	 */
	public BindingResult punchCheck(AttendanceForm forms, BindingResult result) {
		int i = 0;
		for (DailyAttendanceForm form : forms.getAttendanceList()) {
			//備考欄が100文字以上の場合
			if (form.getNote().length() > 100) {
				String[] str = { messageSource.getMessage("placeNote", new String[] {}, Locale.getDefault()), "100" };
				String error = messageUtil.getMessage(Constants.VALID_KEY_MAXLENGTH, str);
				FieldError fieldError = new FieldError(result.getObjectName(), "attendanceList[" + i + "].note", error);
				result.addError(fieldError);
			}
			//出勤時刻の時間、分の分のみが空欄の場合
			if (form.getTrainingStartTimeHour() != null && form.getTrainingStartTimeMinute() == null) {
				String[] str = { "出勤時間" };
				String error = messageUtil.getMessage(Constants.INPUT_INVALID, str);
				FieldError fieldError = new FieldError(result.getObjectName(), "attendanceList[" + i + "].trainingStartTimeMinute", error);
				result.addError(fieldError);
			}
			//出勤時刻の時間、分の時間のみが空欄の場合
			if (form.getTrainingStartTimeMinute() != null && form.getTrainingStartTimeHour() == null) {
				String[] str = { "出勤時間" };
				String error = messageUtil.getMessage(Constants.INPUT_INVALID, str);
				FieldError fieldError = new FieldError(result.getObjectName(), "attendanceList[" + i + "].trainingStartTimeHour", error);
				result.addError(fieldError);
			}
			//退勤時刻の時間、分の分のみが空欄の場合
			if (form.getTrainingEndTimeHour() != null && form.getTrainingEndTimeMinute() == null) {
				String[] str = { "退勤時間" };
				String error = messageUtil.getMessage(Constants.INPUT_INVALID, str);
				FieldError fieldError = new FieldError(result.getObjectName(), "attendanceList[" + i + "].trainingEndTimeMinute", error);
				result.addError(fieldError);
			}
			//退勤時刻の時間、分の時間のみが空欄の場合
			if (form.getTrainingEndTimeMinute() != null && form.getTrainingEndTimeHour() == null) {
				String[] str = { "退勤時間" };
				String error = messageUtil.getMessage(Constants.INPUT_INVALID, str);
				FieldError fieldError = new FieldError(result.getObjectName(), "attendanceList[" + i + "].trainingEndTimeHour", error);
				result.addError(fieldError);
			}
			//出勤時刻が未入力で退勤時刻が入力されている場合
			if (form.getTrainingStartTime() == null && form.getTrainingEndTime() != null) {
				String error = messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
				FieldError fieldError = new FieldError(result.getObjectName(), "attendanceList[" + i + "].trainingStartTimeHour", error);
				result.addError(fieldError);
			}
			//出勤時間、分と退勤時間、分をString型からInteger型に変更
			if (form.getTrainingStartTimeHour() != null
					&& !form.getTrainingStartTimeHour().equals("")
					&& form.getTrainingStartTimeMinute() != null
					&& !form.getTrainingStartTimeMinute().equals("")
					&& form.getTrainingEndTimeHour() != null
					&& !form.getTrainingEndTimeHour().equals("")
					&& form.getTrainingEndTimeMinute() != null
					&& !form.getTrainingEndTimeMinute().equals("")) {
				Integer startHour = Integer.parseInt(form.getTrainingStartTimeHour());
				Integer startMinute = Integer.parseInt(form.getTrainingStartTimeMinute());
				Integer endHour = Integer.parseInt(form.getTrainingEndTimeHour());
				Integer endMinute = Integer.parseInt(form.getTrainingEndTimeMinute());
				//出勤時刻が退勤時刻よりも遅い場合
				if (startHour != null && startMinute != null && endHour != null && endMinute != null) {
					if (startHour > endHour) {
						String[] list = { i + "" };
						String error = messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE, list);
						FieldError fieldError = new FieldError(result.getObjectName(), "attendanceList[" + i + "].trainingStartTimeHour", error);
						result.addError(fieldError);
					} else if (startHour == endHour && startMinute > endMinute) {
						String[] list = { i + "" };
						String error = messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE, list);
						FieldError fieldError = new FieldError(result.getObjectName(), "attendanceList[" + i + "].trainingStartTimeHour", error);
						result.addError(fieldError);
					}
				}
				//中抜け時間が勤務時間よりも長い場合
				TrainingTime trainingTime = new TrainingTime();
				trainingTime.setTrainingStartTime(form.getTrainingStartTime());
				trainingTime.setTrainingEndTime(form.getTrainingEndTime());
				
				trainingTime = attendanceUtil.calcJukoTime(trainingTime);
				if (form.getBlankTime() != null && trainingTime.getTrainingTime() < form.getBlankTime()) {
					String error = messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_BLANKTIMEERROR);
					FieldError fieldError = new FieldError(result.getObjectName(), "attendanceList[" + i + "].blankTime", error);
					result.addError(fieldError);
				}
			
			}
			i++;
		}
		return result;
	}
	
	/**
	 * 中抜け時間と勤怠情報のドロップダウンリストを設定
	 * @param forms
	 * @return　AttendanceForm
	 */
	public AttendanceForm setTime(AttendanceForm forms) {
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
		forms.setHours(attendanceUtil.getHourMap());
		forms.setMinutes(attendanceUtil.getMinuteMap());
		forms.setAttendanceList(newForm);
		return forms;
	}
	
	/**
	 * 勤怠リストを検索
	 * @param form
	 * @return 勤怠リスト
	 */
	public List<AttendanceCheck> getAttendanceData(AttendanceCheckForm form) {
		List<AttendanceCheck> check = tStudentAttendanceMapper.findForAttendanceCheck(form.getUserName(), 
				form.getCourseId(), form.getCompanyId(), form.getPlaceId(), 
				Constants.CODE_VAL_ROLL_STUDENT, Constants.DB_FLG_FALSE);
			
		return check;
	}
}
