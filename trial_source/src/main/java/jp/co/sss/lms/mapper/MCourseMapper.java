package jp.co.sss.lms.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import jp.co.sss.lms.dto.CourseServiceCourseDto;
import jp.co.sss.lms.entity.MCourse;

/**
 * コースマスタマッパー
 * 
 * @author 東京ITスクール
 */
@Mapper
public interface MCourseMapper {

	/**
	 * コース詳細取得
	 * 
	 * @param courseId
	 * @param lmsUserId
	 * @param deleteFlg
	 * @return コース情報サービス コースDTO
	 */
	CourseServiceCourseDto getCourseDetail(@Param("courseId") Integer courseId,
			@Param("deleteFlg") Short deleteFlg);

	/**
	 * コース数取得
	 * 
	 * @param courseId
	 * @return コース数
	 */
	Integer getCourseCount(Integer courseId);
	
	/**
	 * 全件検索
	 * @return 全てのコース
	 */
	List<MCourse> findAll();

}
