package com.vladmihalcea.book.hpjp.jooq.pgsql.score;

import com.vladmihalcea.book.hpjp.hibernate.query.recursive.PostCommentScore;
import com.vladmihalcea.book.hpjp.hibernate.query.recursive.PostCommentScoreResultTransformer;
import com.vladmihalcea.book.hpjp.hibernate.query.recursive.simple.Post;
import com.vladmihalcea.book.hpjp.hibernate.query.recursive.simple.PostComment;
import com.vladmihalcea.book.hpjp.util.AbstractPostgreSQLIntegrationTest;
import org.hibernate.SQLQuery;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

/**
 * <code>PostCommentScoreTest</code> - PostCommentScore Test
 *
 * @author Vlad Mihalcea
 */
public class PostCommentScoreTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[] {
            Post.class,
            PostComment.class
        };
    }

    @Override
    public void init() {
        super.init();
        initData();
    }

    protected void initData() {
        doInJPA(entityManager -> {
            Post post = new Post();
            post.setId(1L);
            post.setTitle("High-Performance Java Persistence");
            entityManager.persist(post);

            PostComment comment1 = new PostComment();
            comment1.setPost(post);
            comment1.setReview("Comment 1");
            comment1.setScore(1);
            entityManager.persist(comment1);

            PostComment comment1_1 = new PostComment();
            comment1_1.setParent(comment1);
            comment1_1.setPost(post);
            comment1_1.setReview("Comment 1_1");
            comment1_1.setScore(2);
            entityManager.persist(comment1_1);

            PostComment comment1_2 = new PostComment();
            comment1_2.setParent(comment1);
            comment1_2.setPost(post);
            comment1_2.setReview("Comment 1_2");
            comment1_2.setScore(2);
            entityManager.persist(comment1_2);

            PostComment comment1_2_1 = new PostComment();
            comment1_2_1.setParent(comment1_2);
            comment1_2_1.setPost(post);
            comment1_2_1.setReview("Comment 1_2_1");
            comment1_2_1.setScore(1);
            entityManager.persist(comment1_2_1);

            PostComment comment2 = new PostComment();
            comment2.setPost(post);
            comment2.setReview("Comment 2");
            comment2.setScore(1);
            entityManager.persist(comment2);

            PostComment comment2_1 = new PostComment();
            comment2_1.setParent(comment2);
            comment2_1.setPost(post);
            comment2_1.setReview("Comment 2_1");
            comment2_1.setScore(1);
            entityManager.persist(comment2_1);

            PostComment comment2_2 = new PostComment();
            comment2_2.setParent(comment2);
            comment2_2.setPost(post);
            comment2_2.setReview("Comment 2_2");
            comment2_2.setScore(1);
            entityManager.persist(comment2_2);

            PostComment comment3 = new PostComment();
            comment3.setPost(post);
            comment3.setReview("Comment 3");
            comment3.setScore(1);
            entityManager.persist(comment3);

            PostComment comment3_1 = new PostComment();
            comment3_1.setParent(comment3);
            comment3_1.setPost(post);
            comment3_1.setReview("Comment 3_1");
            comment3_1.setScore(10);
            entityManager.persist(comment3_1);

            PostComment comment3_2 = new PostComment();
            comment3_2.setParent(comment3);
            comment3_2.setPost(post);
            comment3_2.setReview("Comment 3_2");
            comment3_2.setScore(-2);
            entityManager.persist(comment3_2);

            PostComment comment4 = new PostComment();
            comment4.setPost(post);
            comment4.setReview("Comment 4");
            comment4.setScore(-5);
            entityManager.persist(comment4);

            PostComment comment5 = new PostComment();
            comment5.setPost(post);
            comment5.setReview("Comment 5");
            entityManager.persist(comment5);

            entityManager.flush();

        });
    }

    @Test
    public void test() {
        LOGGER.info("Recursive CTE and Window Functions");
        Long postId = 1L;
        int rank = 3;
        List<PostCommentScore> resultCTEJoin = postCommentScoresCTEJoin(postId, rank);
        assertEquals(3, resultCTEJoin.size());
    }

    protected List<PostCommentScore> postCommentScoresCTEJoin(Long postId, int rank) {
        return doInJPA(entityManager -> {
            List<PostCommentScore> postCommentScores = entityManager.createNativeQuery(
                "SELECT id, parent_id, review, created_on, score " +
                "FROM ( " +
                "    SELECT " +
                "        id, parent_id, review, created_on, score, " +
                "        dense_rank() OVER (ORDER BY total_score DESC) rank " +
                "    FROM ( " +
                "       SELECT " +
                "           id, parent_id, review, created_on, score, " +
                "           SUM(score) OVER (PARTITION BY root_id) total_score " +
                "       FROM (" +
                "          WITH RECURSIVE post_comment_score(id, root_id, post_id, " +
                "              parent_id, review, created_on, score) AS (" +
                "              SELECT " +
                "                  id, id, post_id, parent_id, review, created_on, score" +
                "              FROM post_comment " +
                "              WHERE post_id = :postId AND parent_id IS NULL " +
                "              UNION ALL " +
                "              SELECT pc.id, pcs.root_id, pc.post_id, pc.parent_id, " +
                "                  pc.review, pc.created_on, pc.score " +
                "              FROM post_comment pc " +
                "              INNER JOIN post_comment_score pcs " +
                "              ON pc.parent_id = pcs.id " +
                "              WHERE pc.parent_id = pcs.id " +
                "          ) " +
                "          SELECT id, parent_id, root_id, review, created_on, score " +
                "          FROM post_comment_score " +
                "       ) score_by_comment " +
                "    ) score_total " +
                "    ORDER BY total_score DESC, id ASC " +
                ") total_score_group " +
                "WHERE rank <= :rank", "PostCommentScore").unwrap(SQLQuery.class)
            .setParameter("postId", postId)
            .setParameter("rank", rank)
            .setResultTransformer(new PostCommentScoreResultTransformer())
            .list();
            return postCommentScores;
        });
    }

}
