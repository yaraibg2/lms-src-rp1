package jp.co.sss.lms.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}
