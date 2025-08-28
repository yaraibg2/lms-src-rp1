package jp.co.sss.lms.mapper;

import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import jp.co.sss.lms.dto.UserAttendanceDto;
import jp.co.sss.lms.entity.MPlace;

/**
 * 会場マスタのマッパー
 * @author 東京ITスクール
 */
@Mapper
public interface MPlaceMapper {
	/**
	 * 全件検索
	 * @return 会場リスト
	 */
	List<MPlace> findAll(Short deleteFlg);
	
	/**
	 * 主キー検索
	 * @return 会場
	 */
	MPlace findById(@Param("placeId") Integer placeId, @Param("hiddenFlg") Short hiddenFlg, 
			@Param("deleteFlg") Short deleteFlg);
	
	/**
	 * ユーザー勤怠情報DTOを取得
	 * @param placeId
	 * @param from
	 * @param to
	 * @param deleteFlg
	 * @return ユーザー勤怠情報DTO
	 */
	List<UserAttendanceDto> getUserAttendanceDto(@Param("placeId") Integer placeId, @Param("from") Date from,
			@Param("to") Date to, @Param("deleteFlg") Short deleteFlg);
}
