package jp.co.sss.lms.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import jp.co.sss.lms.entity.MCompany;

/**
 * 企業マスタマッパー
 * @author 東京ITスクール
 */
@Mapper
public interface MCompanyMapper {
	/**
	 * 全件検索
	 * @return
	 */
	List<MCompany> findAll();
}
