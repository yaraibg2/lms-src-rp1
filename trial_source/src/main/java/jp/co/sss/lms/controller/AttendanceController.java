package jp.co.sss.lms.controller;

import java.text.ParseException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.entity.AttendanceCheck;
import jp.co.sss.lms.form.AttendanceCheckForm;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.mapper.MCompanyMapper;
import jp.co.sss.lms.mapper.MCourseMapper;
import jp.co.sss.lms.mapper.MPlaceMapper;
import jp.co.sss.lms.service.StudentAttendanceService;
import jp.co.sss.lms.util.Constants;

/**
 * 勤怠管理コントローラ
 * 
 * @author 東京ITスクール1
 */
@Controller
@RequestMapping("/attendance")
public class AttendanceController {

	@Autowired
	private StudentAttendanceService studentAttendanceService;
	@Autowired
	private LoginUserDto loginUserDto;
	@Autowired
	private MCourseMapper mCourseMapper;
	@Autowired
	private MPlaceMapper mPlaceMapper;
	@Autowired
	private MCompanyMapper mCompanyMapper;

	/**
	 * 勤怠管理画面 初期表示
	 * 
	 * @param lmsUserId
	 * @param courseId
	 * @param model
	 * @return 勤怠管理画面
	 * @throws ParseException
	 */
	@RequestMapping(path = "/detail", method = RequestMethod.GET)
	public String index(Model model) {

		// 勤怠一覧の取得
		List<AttendanceManagementDto> attendanceManagementDtoList = studentAttendanceService
				.getAttendanceManagement(loginUserDto.getCourseId(), loginUserDto.getLmsUserId());
		model.addAttribute("attendanceManagementDtoList", attendanceManagementDtoList);
		//過去日に未入力日があるかをチェック
		boolean check = studentAttendanceService.checkAttendanceBlank(loginUserDto.getLmsUserId());
		model.addAttribute("check", check);

		return "attendance/detail";
	}

	/**
	 * 勤怠管理画面 『出勤』ボタン押下
	 * 
	 * @param model
	 * @return 勤怠管理画面
	 */
	@RequestMapping(path = "/detail", params = "punchIn", method = RequestMethod.POST)
	public String punchIn(Model model) {

		// 更新前のチェック
		String error = studentAttendanceService.punchCheck(Constants.CODE_VAL_ATWORK);
		model.addAttribute("error", error);
		// 勤怠登録
		if (error == null) {
			String message = studentAttendanceService.setPunchIn();
			model.addAttribute("message", message);
		}
		// 一覧の再取得
		List<AttendanceManagementDto> attendanceManagementDtoList = studentAttendanceService
				.getAttendanceManagement(loginUserDto.getCourseId(), loginUserDto.getLmsUserId());
		model.addAttribute("attendanceManagementDtoList", attendanceManagementDtoList);

		return "attendance/detail";
	}

	/**
	 * 勤怠管理画面 『退勤』ボタン押下
	 * 
	 * @param model
	 * @return 勤怠管理画面
	 */
	@RequestMapping(path = "/detail", params = "punchOut", method = RequestMethod.POST)
	public String punchOut(Model model) {

		// 更新前のチェック
		String error = studentAttendanceService.punchCheck(Constants.CODE_VAL_LEAVING);
		model.addAttribute("error", error);
		// 勤怠登録
		if (error == null) {
			String message = studentAttendanceService.setPunchOut();
			model.addAttribute("message", message);
		}
		// 一覧の再取得
		List<AttendanceManagementDto> attendanceManagementDtoList = studentAttendanceService
				.getAttendanceManagement(loginUserDto.getCourseId(), loginUserDto.getLmsUserId());
		model.addAttribute("attendanceManagementDtoList", attendanceManagementDtoList);

		return "attendance/detail";
	}

	/**
	 * 勤怠管理画面 『勤怠情報を直接編集する』リンク押下
	 * 
	 * @param model
	 * @return 勤怠情報直接変更画面
	 */
	@RequestMapping(path = "/update")
	public String update(Model model) {

		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = studentAttendanceService
				.getAttendanceManagement(loginUserDto.getCourseId(), loginUserDto.getLmsUserId());
		// 勤怠フォームの生成
		AttendanceForm attendanceForm = studentAttendanceService
				.setAttendanceForm(attendanceManagementDtoList);
		//出勤時刻、退勤時刻を時間と分に分割
		attendanceForm.setAttendanceList(studentAttendanceService.setTimes(attendanceForm.getAttendanceList()));
		model.addAttribute("hours", studentAttendanceService.setHours());
		model.addAttribute("minutes", studentAttendanceService.setMinutes());
		model.addAttribute("attendanceForm", attendanceForm);

		return "attendance/update";
	}

	/**
	 * 勤怠情報直接変更画面 『更新』ボタン押下
	 * 
	 * @param attendanceForm
	 * @param model
	 * @param result
	 * @return 勤怠管理画面
	 * @throws ParseException
	 */
	@RequestMapping(path = "/update", params = "complete", method = RequestMethod.POST)
	public String complete(@ModelAttribute AttendanceForm attendanceForm, BindingResult result, Model model)
			throws ParseException {
		
		//時間を結合	
		attendanceForm.setAttendanceList(studentAttendanceService.unionTimes(attendanceForm.getAttendanceList()));
		//入力チェック
		result = studentAttendanceService.punchCheck(attendanceForm, result);
		//エラーがある場合
		if (result.hasErrors()) {
			//中抜け時間を設定
			attendanceForm = studentAttendanceService.setBlankTime(attendanceForm);
			// 勤怠フォームの生成
			model.addAttribute("hours", studentAttendanceService.setHours());
			model.addAttribute("minutes", studentAttendanceService.setMinutes());
			model.addAttribute("attendanceForm", attendanceForm);
			return "attendance/update";
		} else {
			// 更新
			String message = studentAttendanceService.update(attendanceForm);
			model.addAttribute("message", message);
		}
		// 一覧の再取得
		List<AttendanceManagementDto> attendanceManagementDtoList = studentAttendanceService
				.getAttendanceManagement(loginUserDto.getCourseId(), loginUserDto.getLmsUserId());
		model.addAttribute("attendanceManagementDtoList", attendanceManagementDtoList);

		return "attendance/detail";
	}
	
	/**
	 * 講師権限ヘッダー 『勤怠確認』リンク押下　
	 * @param attendanceCheckForm
	 * @param model
	 * @return 勤怠情報確認リスト画面
	 */
	@GetMapping("/list")
	public String getList(@ModelAttribute AttendanceCheckForm attendanceCheckForm, Model model) {		//それぞれのリストを取得
		model.addAttribute("courses", mCourseMapper.findAll());
		model.addAttribute("places", mPlaceMapper.findAll());
		model.addAttribute("companies", mCompanyMapper.findAll());
		return "attendance/list";
	}
	/**
	 * 勤怠情報確認リスト画面 『検索』ボタン押下　
	 * @param attendanceCheckForm
	 * @param model
	 * @return 勤怠情報確認リスト画面
	 */
	@PostMapping("/list")
	public String postList(AttendanceCheckForm attendanceCheckForm, Model model) {
		List<AttendanceCheck> checkList = studentAttendanceService.getAttendanceData(attendanceCheckForm);
		//それぞれのリストを取得
		model.addAttribute("checkList", checkList);
		model.addAttribute("courses", mCourseMapper.findAll());
		model.addAttribute("places", mPlaceMapper.findAll());
		model.addAttribute("companies", mCompanyMapper.findAll());
		model.addAttribute("attendanceCheckForm", attendanceCheckForm);
		return "attendance/list";
	}
}